import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.google.gson.Gson;
import object.FunctionStatus;
import object.ResponseMessage;
import object.db.SnsAccount;
import object.db.SnsAccount.Subscription;
import object.request.UpdateSubscriptionListRequest;
import org.apache.log4j.BasicConfigurator;
import service.DynamoDBService;
import service.SNSNotificationService;
import util.CommonUtil;
import util.DBEnumValue;
import util.ErrorMessageUtil;
import util.RequestValidation;

import java.util.*;
import java.util.stream.Collectors;

import static util.DBEnumValue.ArnType.*;
import static util.ErrorMessageUtil.ErrorMessage.*;
import static util.ErrorMessageUtil.ErrorMessage.Request_Format_Error;

public class SubscribeTopic implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    public APIGatewayProxyResponseEvent handleRequest(final APIGatewayProxyRequestEvent input, final Context context) {
        BasicConfigurator.configure();
        final LambdaLogger logger = context.getLogger();
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("X-Custom-Header", "application/json");
        APIGatewayProxyResponseEvent response = new APIGatewayProxyResponseEvent().withHeaders(headers);
        ResponseMessage responseMessage = null;

        if (input != null) {
            Gson gson = new Gson();
            ArrayList<FunctionStatus> fs_all = new ArrayList<>();
            UpdateSubscriptionListRequest request = gson.fromJson(input.getBody(), UpdateSubscriptionListRequest.class);
            //Request Validation
            fs_all.addAll(RequestValidation.updateSubscriptionListRequest_validation(request));
            if (!fs_all.get(fs_all.size() - 1).isStatus())  {
                List<FunctionStatus> filteredList = fs_all.stream().filter(entry -> !entry.isStatus()).collect(Collectors.toList());
                logger.log("\nError subscribe : " + gson.toJson(filteredList));
                List<Object> list_errorMessage = Arrays.asList(gson.fromJson(gson.toJson(filteredList), ResponseMessage.Message[].class));
                return response.withStatusCode(200).withBody(new ResponseMessage(Json_Request_Error.getCode(), list_errorMessage).convertToJsonString());
            }

            //Get Sns Account Info from DB
            fs_all.add(DynamoDBService.getSnsAccount_AppRegId(request.getApp_reg_id()));
            SnsAccount snsAccount = (SnsAccount) fs_all.get(fs_all.size() - 1).getResponse().get("snsAccount");
            if(! fs_all.get(fs_all.size()-1).isStatus()) {
                logger.log("\nError : " + gson.toJson(fs_all));
                return response.withStatusCode(200).withBody(new ResponseMessage(DynamoDB_Query_Error.getCode(), fs_all.get(fs_all.size() - 1).convertToMessage()).convertToJsonString());
            } else if (snsAccount == null){
                logger.log("\nError : " + gson.toJson(fs_all));
                return response.withStatusCode(200).withBody(ErrorMessageUtil.getErrorResponseMessage(AppRegId_Null_Error).convertToJsonString());
            }

            //Topic Subscription

            logger.log("\nsnsAccount Subscriptions : " + gson.toJson(snsAccount.getSubscriptions()));
            //Get the subscribed_record from Sns Account
            List<Subscription> list_subscribed_record = snsAccount.getSubscriptions().stream()
                    .filter(item -> request.getChannel_name().equals(item.getChannel_name()))
                    .collect(Collectors.toList());
            List<String> ref_platform_name = list_subscribed_record.stream().map(item -> item.getRef_platform_name()).collect(Collectors.toList());
            //Get the platform list without subscribed topic from Sns Account
            List<Subscription> platform_list = snsAccount.getSubscriptions().stream()
                    .filter(item -> Platform.toString().equals(item.getChannel_type()) && !ref_platform_name.contains(item.getChannel_name()))
                    .collect(Collectors.toList());
            List<Subscription> list_new_subscribed_record = new ArrayList<>();

            for (Subscription subscription : platform_list) {
                //Check the topic subscribed or not.
                fs_all.add(SNSNotificationService.subscribe(subscription.getArn(), CommonUtil.getSnsTopicArn(request.getChannel_name())));
                if (! fs_all.get(fs_all.size() - 1).isStatus()) {
                    //Subscription fails, Start to unsubscribe the new subscriptions
                    for (Subscription s : list_new_subscribed_record)
                        fs_all.add(SNSNotificationService.unsubscribe(s.getArn()));
                    //Setup Error Response
                    List<FunctionStatus> filteredList = fs_all.stream().filter(entry -> !entry.isStatus()).collect(Collectors.toList());
                    List<Object> list_errorMessage = Arrays.asList(gson.fromJson(gson.toJson(filteredList), ResponseMessage.Message[].class));
                    logger.log("\nError : " + gson.toJson(list_errorMessage));
                    return response.withStatusCode(200).withBody(new ResponseMessage(Sns_Subscription_Error.getCode(), list_errorMessage).convertToJsonString());
                }
                String subscriptionArn = fs_all.get(fs_all.size() - 1).getResponse().get("subscriptionArn").toString();
                list_new_subscribed_record.add(new Subscription(request.getChannel_name(), subscriptionArn, DBEnumValue.ArnType.Topic.toString(), subscription.getChannel_name(), CommonUtil.getCurrentTime()));
            }

            if (list_new_subscribed_record.size() == 0)
                return response.withStatusCode(200).withBody(ErrorMessageUtil.getErrorResponseMessage(Topic_subscription_Already_Error).convertToJsonString());
            //Update DB
            snsAccount.getSubscriptions().addAll(list_new_subscribed_record);
            fs_all.add(DynamoDBService.updateData(snsAccount));
            if (!fs_all.get(fs_all.size() - 1).isStatus()) {
                //Setup Error Response
                List<FunctionStatus> filteredList = fs_all.stream().filter(entry -> !entry.isStatus()).collect(Collectors.toList());
                List<Object> list_errorMessage = Arrays.asList(gson.fromJson(gson.toJson(filteredList), ResponseMessage.Message[].class));
                logger.log("\nError : " + gson.toJson(list_errorMessage));
                return response.withStatusCode(200).withBody(new ResponseMessage(Sns_Subscription_Error.getCode(), list_errorMessage).convertToJsonString());
            }

            responseMessage = new ResponseMessage(200, new ArrayList());
        } else {
            responseMessage = new ResponseMessage(Request_Format_Error.getCode(), Request_Format_Error.getError_msg());
            logger.log("Request Error - Message: " + responseMessage.getMessage());
        }
        return response.withStatusCode(200).withBody(responseMessage.convertToJsonString());
    }
}


