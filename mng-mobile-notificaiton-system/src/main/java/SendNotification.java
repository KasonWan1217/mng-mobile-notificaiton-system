import java.util.*;
import java.util.stream.Collectors;

import com.amazonaws.services.dynamodbv2.model.ExpectedAttributeValue;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import object.FunctionStatus;
import object.db.SnsAccount.Subscription;
import service.DynamoDBService;
import service.SNSNotificationService;
import object.db.InboxRecord;
import object.ResponseMessage;
import org.apache.log4j.BasicConfigurator;
import util.*;

import static util.CommonValidationUtil.intContainsItemFromList;
import static util.ErrorMessageUtil.ErrorMessage.*;

/**
 * Handler for requests to Lambda function.
 */
public class SendNotification implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    public APIGatewayProxyResponseEvent handleRequest(final APIGatewayProxyRequestEvent input, final Context context) {
        String request_time = CommonUtil.getCurrentTime();
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

            InboxRecord recordTable = gson.fromJson(input.getBody(), InboxRecord.class);
            fs_all.addAll(RequestValidation.sendNotification_validation(recordTable));
            if (!fs_all.get(fs_all.size() - 1).isStatus())  {
                List<FunctionStatus> filteredList = fs_all.stream().filter(entry -> !entry.isStatus()).collect(Collectors.toList());
                logger.log("\nError subscribe : " + gson.toJson(filteredList));
                List<Object> list_errorMessage = Arrays.asList(gson.fromJson(gson.toJson(filteredList), ResponseMessage.Message[].class));
                return response.withStatusCode(200).withBody(new ResponseMessage(Json_Request_Error.getCode(), list_errorMessage).convertToJsonString());
            }

            recordTable.setCreate_datetime(request_time);
            if(!recordTable.isDirect_msg()) {
                Map<String, ExpectedAttributeValue> expected = new HashMap<>();
                expected.put("msg_id", new ExpectedAttributeValue(false));
                fs_all.add(DynamoDBService.insertData(recordTable, expected));
                if(! fs_all.get(fs_all.size()-1).isStatus()) {
                    logger.log("\nError : " + gson.toJson(fs_all));
                    return response.withStatusCode(200).withBody(new ResponseMessage(DynamoDB_Insert_Error.getCode(), fs_all.get(fs_all.size() - 1).convertToMessage()).convertToJsonString());
                }
            }
            recordTable.setNotification_id(CommonUtil.genNotificationID(recordTable.getMsg_id(), request_time));
            logger.log("\nsetNotification_id : " + recordTable.getNotification_id());

            ArrayList<String> msg_id_arrayList = new ArrayList<>();
            int msg_qty = 0;

            if (DBEnumValue.TargetType.Personal.toString().equals(recordTable.getTarget_type())) {
                //Send Personal Notification
                //Get all Subscription List form db for Send Push
                fs_all.add(DynamoDBService.getSubscriptionsList(recordTable.getTarget()));
                if(! fs_all.get(fs_all.size()-1).isStatus()) {
                    fs_all.add(DynamoDBService.deleteData(recordTable));
                    List<FunctionStatus> filteredList = fs_all.stream().filter(entry -> !entry.isStatus()).collect(Collectors.toList());
                    logger.log("\nError subscribe : " + gson.toJson(filteredList));
                    List<Object> message = Arrays.asList(gson.fromJson(gson.toJson(filteredList), ResponseMessage.Message[].class));
                    return response.withStatusCode(200).withBody(new ResponseMessage(DynamoDB_Query_Error.getCode(), message).convertToJsonString());
                }

                for (Subscription subscriptions : (List<Subscription>) fs_all.get(fs_all.size() - 1).getResponse().get("arrayList_channelName")) {
                    if (DBEnumValue.ArnType.Platform.toString().equals(subscriptions.getChannel_type())) {
                        fs_all.add(new SNSNotificationService().publishNotification(recordTable, subscriptions.getArn()));
                        if (!fs_all.get(fs_all.size() - 1).isStatus()) {
                            logger.log("\nError : " + gson.toJson(fs_all));
                            return response.withStatusCode(200).withBody(new ResponseMessage(Sns_Publish_Notification_Error.getCode(), fs_all.get(fs_all.size() - 1).convertToMessage()).convertToJsonString());
                        }
                        msg_id_arrayList.add(fs_all.get(fs_all.size() - 1).getResponse().get("msg_id").toString());
                        msg_qty++;
                    }
                }
            } else {
                //Send Group Notification
                fs_all.add(new SNSNotificationService().publishNotification(recordTable, CommonUtil.getSnsTopicArn(recordTable.getTarget())));
                if(! fs_all.get(fs_all.size()-1).isStatus()) {
                    logger.log("\nError : " + gson.toJson(fs_all));
                    return response.withStatusCode(200).withBody(new ResponseMessage(Sns_Publish_Notification_Error.getCode(), fs_all.get(fs_all.size() - 1).convertToMessage()).convertToJsonString());
                }
                msg_id_arrayList.add(fs_all.get(fs_all.size()-1).getResponse().get("msg_id").toString());
                msg_qty = (int) fs_all.get(fs_all.size()-1).getResponse().get("msg_qty");
            }
            recordTable.setSns_msg_id(msg_id_arrayList);
            recordTable.setMsg_qty(msg_qty);

            //Check QTY of Notification
            if (recordTable.getMsg_qty() == 0) {
                fs_all.add(DynamoDBService.deleteData(recordTable));
                output = new ResponseMessage(Sns_Publish_Notification_Error.getCode(), fs_all.get(fs_all.size() - 1).convertToMessage());
            } else {
                if (!recordTable.isDirect_msg() && recordTable.getMsg_qty() > 0) {
                    fs_all.add(DynamoDBService.updateData(recordTable));
                    if (!fs_all.get(fs_all.size() - 1).isStatus()) {
                        logger.log("\nError : " + gson.toJson(fs_all));
                        return response.withStatusCode(200).withBody(new ResponseMessage(DynamoDB_Update_Error.getCode(), fs_all.get(fs_all.size() - 1).convertToMessage()).convertToJsonString());
                    }
                }
                logger.log("Send Notification Success.");
                ResponseMessage.Message rs_msg = new ResponseMessage.Message();
                rs_msg.setMsg_qty(String.valueOf(recordTable.getMsg_qty()));
                output = new ResponseMessage(200, rs_msg);
            }
        } else {
            output = new ResponseMessage(Request_Format_Error.getCode(), Request_Format_Error.getError_msg());
            logger.log("Request Error - Message: " + output.getMessage());
        }
        return response.withStatusCode(200).withBody(output.convertToJsonString());
    }
}

