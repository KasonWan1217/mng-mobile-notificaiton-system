import com.amazonaws.services.dynamodbv2.model.ExpectedAttributeValue;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.google.gson.Gson;
import object.FunctionStatus;
import object.db.ApplicationChannel;
import object.db.SnsAccount;
import object.db.SnsAccount.Subscription;
import object.ResponseMessage;
import org.apache.log4j.BasicConfigurator;
import service.DynamoDBService;
import service.SNSNotificationService;
import util.CommonUtil;
import util.DBEnumValue.*;
import util.ErrorMessageUtil;
import util.RequestValidation;

import java.util.*;
import java.util.stream.Collectors;

import static util.ErrorMessageUtil.ErrorMessage.*;

public class RegisterSnsService implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    public APIGatewayProxyResponseEvent handleRequest(final APIGatewayProxyRequestEvent input, final Context context) {
        BasicConfigurator.configure();
        final LambdaLogger logger = context.getLogger();
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("X-Custom-Header", "application/json");
        APIGatewayProxyResponseEvent response = new APIGatewayProxyResponseEvent().withHeaders(headers);
        ResponseMessage output = null;

        if (input != null) {
            Gson gson = new Gson();
            ArrayList<FunctionStatus> fs_all = new ArrayList<>();
            SnsAccount snsAccount;
            SnsAccount oldAccount;

            //Json validation
            try {
                snsAccount = gson.fromJson(input.getBody(), SnsAccount.class);
                fs_all.addAll(RequestValidation.registerSnsService_validation(snsAccount));
                if (!fs_all.get(fs_all.size() - 1).isStatus())  {
                    List<FunctionStatus> filteredList = fs_all.stream().filter(entry -> !entry.isStatus()).collect(Collectors.toList());
                    logger.log("\nError subscribe : " + gson.toJson(filteredList));
                    List<Object> list_errorMessage = Arrays.asList(gson.fromJson(gson.toJson(filteredList), ResponseMessage.Message[].class));
                    return response.withStatusCode(200).withBody(new ResponseMessage(Json_Request_Error.getCode(), list_errorMessage).convertToJsonString());
                }
            } catch(Exception e) {
                fs_all.add(ErrorMessageUtil.getFunctionStatus(Json_Request_Error, input.getBody()));
                List<FunctionStatus> filteredList = fs_all.stream().filter(entry -> !entry.isStatus()).collect(Collectors.toList());
                logger.log("\nError Json Request : " + gson.toJson(filteredList));
                List<Object> list_errorMessage = Arrays.asList(gson.fromJson(gson.toJson(filteredList), ResponseMessage.Message[].class));
                return response.withStatusCode(200).withBody(new ResponseMessage(Json_Request_Error.getCode(), list_errorMessage).convertToJsonString());
            }
            //end of validation


            //Try to retrieve account record by DeviceToken or AppRegId
            if (snsAccount.getApp_reg_id() != null && snsAccount.getApp_reg_id().isEmpty()) {
                fs_all.add(DynamoDBService.getSnsAccount_AppRegId(snsAccount.getApp_reg_id()));
            } else {
                fs_all.add(DynamoDBService.getSnsAccount_DeviceToken(snsAccount.getDevice_token()));
            }
            if (!fs_all.get(fs_all.size() - 1).isStatus()) {
                logger.log("\nError : " + gson.toJson(fs_all));
                return response.withStatusCode(200).withBody(new ResponseMessage(DynamoDB_Query_Error.getCode(), fs_all.get(fs_all.size() - 1).convertToMessage()).convertToJsonString());
            }


            oldAccount = (SnsAccount) fs_all.get(fs_all.size() - 1).getResponse().get("snsAccount");
            boolean reg_device_token = true;
            boolean update_device_token = false;
            List<ApplicationChannel> pre_subscribe_platform = null;
            List<ApplicationChannel> pre_subscribe_topic = null;
            //Processing of existing cases
            if (oldAccount != null) {
                if (!oldAccount.getDevice_token().equals(snsAccount.getDevice_token())) {
                    //update device token + reset account, (check datetime)
                    //reg_device_token = true;
                    update_device_token = true;

                    fs_all.add(SNSNotificationService.resetAccount(oldAccount.getSubscriptions()));
                    oldAccount.setDevice_token(snsAccount.getDevice_token());
                    snsAccount = oldAccount;
                    snsAccount.setSubscriptions(null);
                    //update subscription
                } else if (!oldAccount.getApp_id().equals(snsAccount.getApp_id())) {
                    //register new account + reset account, update status,
                    //reg_device_token = true;
                    //update_device_token = false;
                    fs_all.add(SNSNotificationService.resetAccount(oldAccount.getSubscriptions()));
                    oldAccount.setActive_status(Status.Reset.toString());
                    logger.log("\nReset Account : " + oldAccount.convertToJsonString());
                    DynamoDBService.updateData(oldAccount);
                    //new account
                } else {
                    //account exists break all function
                    reg_device_token = false;
                    //update_device_token = false;
                    ResponseMessage.Message rs_msg = new ResponseMessage.Message();
                    rs_msg.setApp_reg_id(snsAccount.getApp_reg_id());
                    rs_msg.setDatetime(snsAccount.getCreate_datetime());
                    output = new ResponseMessage(200, rs_msg);
                }
            }


            if (reg_device_token) {
                //Set up SnsAccount & pre_subscribe_list
                fs_all.add(DynamoDBService.getApplicationChannelList(snsAccount.getApp_name()));
                if (!fs_all.get(fs_all.size() - 1).isStatus()) {
                    logger.log("\nError : " + gson.toJson(fs_all));
                    return response.withStatusCode(200).withBody(new ResponseMessage(DynamoDB_Query_Error.getCode(), fs_all.get(fs_all.size() - 1).convertToMessage()).convertToJsonString());
                }
                List<ApplicationChannel> temp_ApplicationChannel = (List<ApplicationChannel>) fs_all.get(fs_all.size() - 1).getResponse().get("ChannelList");

                if (!update_device_token) {
                    //setup new account
                    logger.log("\nCreate Account");
                    snsAccount.setActive_status(Status.Fail.toString());
                    snsAccount.setCreate_datetime(CommonUtil.getCurrentTime());
                    snsAccount.setApp_reg_id(CommonUtil.getNewAppRegID(snsAccount.getApp_id(), snsAccount.getCreate_datetime()));
                    logger.log("\nSet App_reg_id : " + snsAccount.getApp_reg_id());
                    Map<String, ExpectedAttributeValue> expected = new HashMap<>();
                    expected.put("app_reg_id", new ExpectedAttributeValue(false));
                    fs_all.add(DynamoDBService.insertData(snsAccount, expected));
                    if (!fs_all.get(fs_all.size() - 1).isStatus()) {
                        logger.log("\nError : " + gson.toJson(fs_all));
                        return response.withStatusCode(200).withBody(new ResponseMessage(DynamoDB_Insert_Error.getCode(), fs_all.get(fs_all.size() - 1).convertToMessage()).convertToJsonString());
                    }

                    //setup Application Channel List
                    String mobile_type = snsAccount.getMobile_type();
                    pre_subscribe_platform = temp_ApplicationChannel.stream().filter(entry -> (entry.getChannel_type().equals(ArnType.Platform.toString()) && entry.getMobile_type().equals(mobile_type))).collect(Collectors.toList());
                    pre_subscribe_topic = temp_ApplicationChannel.stream().filter(entry ->  entry.getChannel_type().equals(ArnType.Topic.toString())).collect(Collectors.toList());
                }else {
                    List<Subscription> temp_topic = oldAccount.getSubscriptions().stream().filter(entry ->  entry.getChannel_type().equals(ArnType.Topic.toString())).collect(Collectors.toList());
                    pre_subscribe_topic = Arrays.asList(gson.fromJson(gson.toJson(temp_topic), ApplicationChannel[].class));
                }
                //End of Set up SnsAccount & pre_subscribe_list

                //Start the Registration & Subscription
                //token_registration - check the register Success/Fail
                boolean token_registration = false;
                //Platform registration
                for (ApplicationChannel channel : pre_subscribe_platform) {
                    //token_registration - check the topic subscription Success/Fail
                    boolean topic_subscription = false;
                    Subscription table_Subscriptions = new Subscription(channel.getChannel_name(), "", ArnType.Platform.toString(), snsAccount.getCreate_datetime());

                    //Device token registration
                    fs_all.add(SNSNotificationService.register(snsAccount.getDevice_token(), CommonUtil.getSnsPlatformArn(table_Subscriptions.getChannel_name())));
                    if (fs_all.get(fs_all.size() - 1).isStatus()) {
                        String endpoint_platform = (String) fs_all.get(fs_all.size() - 1).getResponse().get("endpointArn");
                        table_Subscriptions.setArn(endpoint_platform);
                        snsAccount.addSubscriptions(table_Subscriptions);

                        //Topic subscription
                        for (ApplicationChannel topic : pre_subscribe_topic) {
                            String pre_subscribe_topic_name = CommonUtil.getSnsTopicArn(topic.getChannel_name()) + "_" + CommonUtil.getLastWord(snsAccount.getCreate_datetime(), 2);
                            fs_all.add(SNSNotificationService.subscribe(table_Subscriptions.getArn(), pre_subscribe_topic_name));
                            if (!fs_all.get(fs_all.size() - 1).isStatus()) {
                                //unRegister Platform
                                fs_all.add(SNSNotificationService.unregister(table_Subscriptions.getArn()));
                                topic_subscription = false;
                                break;
                            } else {
                                String subscriptionArn = (String) fs_all.get(fs_all.size() - 1).getResponse().get("subscriptionArn");
                                Subscription table_sub_topic = new Subscription(topic.getChannel_name() + "_" + CommonUtil.getLastWord(snsAccount.getCreate_datetime(),2), subscriptionArn, ArnType.Topic.toString(), channel.getChannel_name(), snsAccount.getCreate_datetime());
                                snsAccount.addSubscriptions(table_sub_topic);
                                topic_subscription = true;
                            }
                        }
                        if (!topic_subscription) {
                            fs_all.add(SNSNotificationService.unregister(table_Subscriptions.getArn()));
                            for (Subscription subscription : snsAccount.getSubscriptions()) {
                                fs_all.add(SNSNotificationService.unsubscribe(subscription.getArn()));
                            }
                            //snsAccount.getSubscriptions().removeIf(item -> table_Subscriptions.getChannel_name().equals(item.getRef_platform_name()) || table_Subscriptions.getChannel_name().equals(item.getChannel_name()));
                            List<FunctionStatus> filteredList = fs_all.stream().filter(entry -> !entry.isStatus()).collect(Collectors.toList());
                            logger.log("\nError subscribe : " + gson.toJson(filteredList));
                            List<Object> list_errorMessage = Arrays.asList(gson.fromJson(gson.toJson(filteredList), ResponseMessage.Message[].class));
                            return response.withStatusCode(200).withBody(new ResponseMessage(Sns_Subscription_Error.getCode(), list_errorMessage).convertToJsonString());
                        } else {
                            token_registration = true;
                        }
                    }
                }
                //End the Registration & Subscription

                //Subscription Success/Fail Action
                if (token_registration) {
                    //Update Status & Subscriptions.
                    logger.log("registerSuccess");
                    snsAccount.setActive_status(Status.Success.toString());
                    fs_all.add(DynamoDBService.updateData(snsAccount));
                    if (!fs_all.get(fs_all.size() - 1).isStatus()) {
                        logger.log("\nError : " + gson.toJson(fs_all));
                        return response.withStatusCode(200).withBody(new ResponseMessage(DynamoDB_Update_Error.getCode(), fs_all.get(fs_all.size() - 1).convertToMessage()).convertToJsonString());
                    }
                    logger.log("registerSuccess");
                    ResponseMessage.Message rs_msg = new ResponseMessage.Message();
                    rs_msg.setApp_reg_id(snsAccount.getApp_reg_id());
                    rs_msg.setDatetime(snsAccount.getCreate_datetime());
                    output = new ResponseMessage(200, rs_msg);
                } else {
                    //All Platform Registration Fails
                    List<FunctionStatus> filteredList = fs_all.stream().filter(entry -> !entry.isStatus()).collect(Collectors.toList());
                    logger.log("\nError : " + gson.toJson(filteredList));
                    logger.log("\nError : " + filteredList);
                    List<Object> message = Arrays.asList(gson.fromJson(gson.toJson(filteredList), ResponseMessage.Message[].class));
                    return response.withStatusCode(200).withBody(new ResponseMessage(Sns_Registration_Error.getCode(), message).convertToJsonString());
                }
            }
            
        } else {
            output = new ResponseMessage(Request_Format_Error.getCode(), Request_Format_Error.getError_msg());
            logger.log("Request Error - Message: " + output.getMessage());
        }
        return response.withStatusCode(200).withBody(output.convertToJsonString());
    }
}
