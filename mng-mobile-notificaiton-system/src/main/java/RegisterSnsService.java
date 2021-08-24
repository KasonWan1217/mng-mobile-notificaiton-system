import com.amazonaws.services.dynamodbv2.model.ExpectedAttributeValue;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
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
        ResponseMessage output;

        if (input != null) {
            Gson gson = new Gson();
            ArrayList<FunctionStatus> fs_all = new ArrayList<>();
            SnsAccount snsAccount = gson.fromJson(input.getBody(), SnsAccount.class);
            fs_all.addAll(RequestValidation.registerSnsService_validation(snsAccount));
            if (!fs_all.get(fs_all.size() - 1).isStatus())  {
                List<FunctionStatus> filteredList = fs_all.stream().filter(entry -> !entry.isStatus()).collect(Collectors.toList());
                logger.log("\nError subscribe : " + gson.toJson(filteredList));
                List<Object> list_errorMessage = Arrays.asList(gson.fromJson(gson.toJson(filteredList), ResponseMessage.Message[].class));
                return response.withStatusCode(200).withBody(new ResponseMessage(Json_Request_Error.getCode(), list_errorMessage).convertToJsonString());
            }

            if (snsAccount.getApp_reg_id() == null || snsAccount.getApp_reg_id().isEmpty()) {
                //Try to get Old device token Info
                fs_all.add(DynamoDBService.getSnsAccount_DeviceToken(snsAccount.getDevice_token()));
                if (!fs_all.get(fs_all.size() - 1).isStatus()) {
                    logger.log("\nError : " + gson.toJson(fs_all));
                    return response.withStatusCode(200).withBody(new ResponseMessage(DynamoDB_Query_Error.getCode(), fs_all.get(fs_all.size() - 1).convertToMessage()).convertToJsonString());
                }
                //Reset old Account and Update status
                SnsAccount oldAccount = (SnsAccount) fs_all.get(fs_all.size() - 1).getResponse().get("snsAccount");
                logger.log("\noldAccount : " + gson.toJson(oldAccount));
                if (oldAccount != null) {
                    fs_all.add(SNSNotificationService.resetAccount(oldAccount.getSubscriptions()));
                    oldAccount.setActive_status(Status.Reset.toString());
                    logger.log("\nReset Account : " + oldAccount.convertToJsonString());
                    DynamoDBService.updateData(oldAccount);
                }

                //Create new Account
                logger.log("\nCreate Account");
                snsAccount.setActive_status(Status.Fail.toString());
                snsAccount.setCreate_datetime(CommonUtil.getCurrentTime());
                snsAccount.setApp_reg_id(CommonUtil.getNewAppRegID(snsAccount.getApp_id(), snsAccount.getCreate_datetime()));
                logger.log("\nSet App_reg_id : " +  snsAccount.getApp_reg_id());
                Map<String, ExpectedAttributeValue> expected = new HashMap<>();
                expected.put("app_reg_id", new ExpectedAttributeValue(false));
                fs_all.add(DynamoDBService.insertData(snsAccount, expected));
                if (!fs_all.get(fs_all.size() - 1).isStatus()) {
                    logger.log("\nError : " + gson.toJson(fs_all));
                    return response.withStatusCode(200).withBody(new ResponseMessage(DynamoDB_Insert_Error.getCode(), fs_all.get(fs_all.size() - 1).convertToMessage()).convertToJsonString());
                }

                //Get all platform name form db for device token registration
                fs_all.add(DynamoDBService.getChannelList(snsAccount.getApp_name(), snsAccount.getMobile_type()));
                if (!fs_all.get(fs_all.size() - 1).isStatus()) {
                    logger.log("\nError : " + gson.toJson(fs_all));
                    return response.withStatusCode(200).withBody(new ResponseMessage(DynamoDB_Query_Error.getCode(), fs_all.get(fs_all.size() - 1).convertToMessage()).convertToJsonString());
                }

                //Check the register Success
                boolean registerSuccess = false;
                //Platform registration
                for (ApplicationChannel channel : (List<ApplicationChannel>) fs_all.get(fs_all.size() - 1).getResponse().get("ApplicationChannelList")) {
                    Subscription table_Subscriptions = new Subscription(channel.getChannel_name(), "", ArnType.Platform.toString(), snsAccount.getCreate_datetime());
                    //Device token registration
                    fs_all.add(SNSNotificationService.register(snsAccount.getDevice_token(), CommonUtil.getSnsPlatformArn(table_Subscriptions.getChannel_name())));
                    if (fs_all.get(fs_all.size() - 1).isStatus()) {
                        String endpoint_platform = (String) fs_all.get(fs_all.size() - 1).getResponse().get("endpointArn");
                        table_Subscriptions.setArn(endpoint_platform);

                        //EndpointArn subscription
                        fs_all.add(SNSNotificationService.subscribe(table_Subscriptions.getArn(), CommonUtil.getSnsTopicArn(AppName.BEA_APP_Group.toString())));
                        if (! fs_all.get(fs_all.size() - 1).isStatus()) {
                            //unRegister Platform
                            fs_all.add(SNSNotificationService.unregister(table_Subscriptions.getArn()));
                            List<FunctionStatus> filteredList = fs_all.stream().filter(entry -> !entry.isStatus()).collect(Collectors.toList());
                            logger.log("\nError subscribe : " + gson.toJson(filteredList));
                            List<Object> list_errorMessage = Arrays.asList(gson.fromJson(gson.toJson(filteredList), ResponseMessage.Message[].class));
                            return response.withStatusCode(200).withBody(new ResponseMessage(Sns_Subscription_Error.getCode(), list_errorMessage).convertToJsonString());
                        } else {
                            String subscriptionArn = (String) fs_all.get(fs_all.size() - 1).getResponse().get("subscriptionArn");
                            Subscription table_sub_topic = new Subscription(AppName.BEA_APP_Group.toString(), subscriptionArn, ArnType.Topic.toString(), channel.getChannel_name(), snsAccount.getCreate_datetime());
                            snsAccount.addSubscriptions(table_Subscriptions);
                            snsAccount.addSubscriptions(table_sub_topic);
                            registerSuccess = true;
                        }
                    }
                }

                if (registerSuccess) {
                    //Update Status & Subscriptions.
                    logger.log("registerSuccess");
                    snsAccount.setActive_status(Status.Success.toString());
                    fs_all.add(DynamoDBService.updateData(snsAccount));
                    if (!fs_all.get(fs_all.size() - 1).isStatus()) {
                        logger.log("\nError : " + gson.toJson(fs_all));
                        return response.withStatusCode(200).withBody(new ResponseMessage(DynamoDB_Update_Error.getCode(), fs_all.get(fs_all.size() - 1).convertToMessage()).convertToJsonString());
                    }
                    logger.log("registerSuccess");
                } else {
                    //All Platform Registration Fails OR Topic Subscription Fails
                    List<FunctionStatus> filteredList = fs_all.stream().filter(entry -> !entry.isStatus()).collect(Collectors.toList());
                    logger.log("\nError : " + gson.toJson(filteredList));
                    logger.log("\nError : " + filteredList.toString());
                    List<Object> message = Arrays.asList(gson.fromJson(gson.toJson(filteredList), ResponseMessage.Message[].class));
                    return response.withStatusCode(200).withBody(new ResponseMessage(Sns_Registration_Error.getCode(), message).convertToJsonString());
                }

            }

            if (CommonUtil.validate_AppRegId(snsAccount.getApp_reg_id())) {
                if (snsAccount.getApp_reg_id() != null && !snsAccount.getApp_reg_id().isEmpty()) {
                    ResponseMessage.Message rs_msg = new ResponseMessage.Message();
                    rs_msg.setApp_reg_id(snsAccount.getApp_reg_id());
                    rs_msg.setDatetime(snsAccount.getCreate_datetime());
                    output = new ResponseMessage(200, rs_msg);
                } else {
                    output = ErrorMessageUtil.getErrorResponseMessage(AppRegId_Null_Error);
                }
            } else {
                output = ErrorMessageUtil.getErrorResponseMessage(AppRegId_Invalid_Error);
            }
        } else {
            output = new ResponseMessage(Request_Format_Error.getCode(), Request_Format_Error.getError_msg());
            logger.log("Request Error - Message: " + output.getMessage());
        }
        return response.withStatusCode(200).withBody(output.convertToJsonString());
    }
}
