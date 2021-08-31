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
            if (snsAccount.getApp_reg_id() != null && !snsAccount.getApp_reg_id().isEmpty()) {
                logger.log("\nGet Existing Account by AppRegId");
                fs_all.add(DynamoDBService.getSnsAccount_AppRegId(snsAccount.getApp_reg_id()));
            } else {
                logger.log("\nGet Existing Account by DeviceToken");
                fs_all.add(DynamoDBService.getSnsAccount_DeviceToken(snsAccount.getDevice_token()));
            }
            if (!fs_all.get(fs_all.size() - 1).isStatus()) {
                logger.log("\nError : " + gson.toJson(fs_all));
                return response.withStatusCode(200).withBody(new ResponseMessage(DynamoDB_Query_Error.getCode(), fs_all.get(fs_all.size() - 1).convertToMessage()).convertToJsonString());
            }


            oldAccount = (SnsAccount) fs_all.get(fs_all.size() - 1).getResponse().get("snsAccount");
            boolean reg_device_token = true;
            boolean update_device_token = false;
            //Processing of existing cases
            if (oldAccount != null) {
                logger.log("\nProcess the Existing Account");
                if (!oldAccount.getApp_reg_id().equals(snsAccount.getApp_reg_id())) {
                    logger.log("\nReset the existing Account, and need to create the new Account");
                    //register new account + reset account, update status,
                    //reg_device_token = true;
                    //update_device_token = false;
                    fs_all.add(SNSNotificationService.resetAccount(oldAccount.getSubscriptions()));
                    oldAccount.setActive_status(Status.Reset.toString());
                    logger.log("\nReset Account : " + oldAccount.convertToJsonString());
                    DynamoDBService.updateData(oldAccount);
                    //new account
                } else if (!oldAccount.getDevice_token().equals(snsAccount.getDevice_token())) {
                    logger.log("\nNeed to update the Device Token");
                    //update device token + reset account, (check datetime)
                    //reg_device_token = true;
                    update_device_token = true;

                    fs_all.add(SNSNotificationService.resetAccount(oldAccount.getSubscriptions()));
                    oldAccount.setDevice_token(snsAccount.getDevice_token());
                    snsAccount = new SnsAccount(oldAccount);
                    logger.log("\nSubL:" + oldAccount.getSubscriptions().size());
                    snsAccount.setSubscriptions(null);
                    logger.log("\nSubL:" + oldAccount.getSubscriptions().size());
                    //update subscription
                } else {
                    logger.log("\nAll Account data Correct");
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
                logger.log("\nGet Application Channel List");
                //Set up SnsAccount & pre_subscribe_list
                fs_all.add(DynamoDBService.getApplicationChannelList(snsAccount.getApp_name()));
                if (!fs_all.get(fs_all.size() - 1).isStatus()) {
                    logger.log("\nError : " + gson.toJson(fs_all));
                    return response.withStatusCode(200).withBody(new ResponseMessage(DynamoDB_Query_Error.getCode(), fs_all.get(fs_all.size() - 1).convertToMessage()).convertToJsonString());
                }
                //setup Application Channel List
                List<ApplicationChannel> temp_ApplicationChannel = (List<ApplicationChannel>) fs_all.get(fs_all.size() - 1).getResponse().get("ChannelList");
                String mobile_type = snsAccount.getMobile_type();
                List<ApplicationChannel> list_pre_subscribe_platform = temp_ApplicationChannel.stream().filter(entry -> (entry.getChannel_type().equals(ArnType.Platform.toString()) && entry.getMobile_type().equals(mobile_type))).collect(Collectors.toList());
                List<ApplicationChannel> list_pre_subscribe_topic = temp_ApplicationChannel.stream().filter(entry ->  entry.getChannel_type().equals(ArnType.Topic.toString())).collect(Collectors.toList());

                if (!update_device_token) {
                    //setup new account
                    logger.log("\nSetup New Account & Application Channel List");
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
                } else {
                    logger.log("\nUpdate Application Topic List - Update Device Token");
                    //setup Application Topic List
                    List<Subscription> temp_topic = oldAccount.getSubscriptions().stream().filter(entry -> entry.getChannel_type().equals(ArnType.Topic.toString())).collect(Collectors.toList());
                    logger.log("\nUpdate Device Token\n"+gson.toJson(temp_topic));
                    temp_topic.stream().forEach(c -> c.setChannel_name(CommonUtil.getStartIndex(c.getChannel_name(),3)));
                    logger.log("\nUpdate Device Token\n"+gson.toJson(temp_topic));
                    temp_topic = temp_topic.stream().filter(t -> list_pre_subscribe_topic.stream().noneMatch(l -> l.getChannel_name().equals(t.getChannel_name()))).collect(Collectors.toList());
                    logger.log("\nUpdate Device Token\n"+gson.toJson(temp_topic));
                    list_pre_subscribe_topic.addAll(Arrays.asList(gson.fromJson(gson.toJson(temp_topic), ApplicationChannel[].class)));
                    //listA.addAll(listB.stream().filter(t -> listA.stream().noneMatch(u -> areEqual.test(t, u))).collect(Collectors.toList())
//                    for (ApplicationChannel temp_all_c : list_pre_subscribe_topic) {
//                        temp_topic.stream().filter(c -> c.getChannel_name().equals(temp_all_c.getChannel_name())).collect(Collectors.toList());
//                    }
//                    list_pre_subscribe_topic.addAll(Arrays.asList(gson.fromJson(gson.toJson(temp_topic), ApplicationChannel[].class)));
                }
                //List<FunctionStatus> filteredList = fs_all.stream().filter(entry -> sting1.contains("sss")).collect(Collectors.toList());
                //End of Set up SnsAccount & pre_subscribe_list

                //Start the Registration & Subscription
                //token_registration - check the register Success/Fail
                //boolean token_registration = false;
                //Platform registration
                boolean topic_subscription_status = false, channel_subscription_status = false;
                for (ApplicationChannel channel : list_pre_subscribe_platform) {
                    channel_subscription_status = false;
                    logger.log("\nStart to Subscribe channel");
                    //token_registration - check the topic subscription Success/Fail
                    Subscription channel_subscription = new Subscription(channel.getChannel_name(), "", ArnType.Platform.toString(), snsAccount.getCreate_datetime());

                    //Device token registration
                    fs_all.add(SNSNotificationService.register(snsAccount.getDevice_token(), CommonUtil.getSnsPlatformArn(channel_subscription.getChannel_name())));
                    if (fs_all.get(fs_all.size() - 1).isStatus()) {
                        channel_subscription_status = true;
                        String endpoint_platform = (String) fs_all.get(fs_all.size() - 1).getResponse().get("endpointArn");
                        channel_subscription.setArn(endpoint_platform);
                        snsAccount.addSubscriptions(channel_subscription);

                        //Topic subscription
                        for (ApplicationChannel temp_topic : list_pre_subscribe_topic) {
                            topic_subscription_status = false;
                            logger.log("\nStart to Subscribe topic");
                            String pre_subscribe_topic_name = temp_topic.getChannel_name() + "_" + CommonUtil.getEndIndex(snsAccount.getCreate_datetime(), 12);
                            logger.log("\ntopic: "+ pre_subscribe_topic_name);
                            Subscription topic_subscription = new Subscription(pre_subscribe_topic_name, "", ArnType.Topic.toString(), channel.getChannel_name(), snsAccount.getCreate_datetime());
                            fs_all.add(SNSNotificationService.subscribe(channel_subscription.getArn(), CommonUtil.getSnsTopicArn(pre_subscribe_topic_name)));
                            if (fs_all.get(fs_all.size() - 1).isStatus()) {
                                String subscriptionArn = (String) fs_all.get(fs_all.size() - 1).getResponse().get("subscriptionArn");
                                topic_subscription.setArn(subscriptionArn);
                                snsAccount.addSubscriptions(topic_subscription);
                                topic_subscription_status = true;
                            } else {
                                logger.log("\nFailed to Subscribe topic");
                                break;
                            }
                        }
                        if (!topic_subscription_status) {
                            logger.log("\nFailed to Subscribe topic");
                            break;
                        }
                    } else {
                        channel_subscription_status = false;
                        break;
                    }
                }
                //End the Registration & Subscription

                //Subscription Success/Fail Action
                if (channel_subscription_status && topic_subscription_status) {
                    //Update Status & Subscriptions.
                    logger.log("Subscribe channel Success");
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
                    //Failed to Subscribe channel
                    logger.log("\nFailed to Subscribe channel");
                    fs_all.add(SNSNotificationService.resetAccount(snsAccount.getSubscriptions()));
                    List<FunctionStatus> filteredList = fs_all.stream().filter(entry -> !entry.isStatus()).collect(Collectors.toList());
                    logger.log("\nError subscribe : " + gson.toJson(filteredList));
                    List<Object> list_errorMessage = Arrays.asList(gson.fromJson(gson.toJson(filteredList), ResponseMessage.Message[].class));
                    return response.withStatusCode(200).withBody(new ResponseMessage(Sns_Subscription_Error.getCode(), list_errorMessage).convertToJsonString());
                }
            }
            
        } else {
            output = new ResponseMessage(Request_Format_Error.getCode(), Request_Format_Error.getError_msg());
            logger.log("Request Error - Message: " + output.getMessage());
        }
        return response.withStatusCode(200).withBody(output.convertToJsonString());
    }
}
