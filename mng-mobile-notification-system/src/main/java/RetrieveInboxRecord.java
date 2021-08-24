import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import object.FunctionStatus;
import object.InboxMessageRecord;
import object.ResponseMessage;
import object.db.SnsAccount.Subscription;
import object.request.RetrieveInboxRecordRequest;
import org.apache.log4j.BasicConfigurator;
import service.DynamoDBService;
import util.RequestValidation;

import java.util.*;
import java.util.stream.Collectors;

import static util.ErrorMessageUtil.ErrorMessage.*;

public class RetrieveInboxRecord implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

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
            RetrieveInboxRecordRequest request = gson.fromJson(input.getBody(), RetrieveInboxRecordRequest.class);
            //Request Validation
            fs_all.addAll(RequestValidation.retrieveInboxRecord_validation(request));
            if (!fs_all.get(fs_all.size() - 1).isStatus())  {
                List<FunctionStatus> filteredList = fs_all.stream().filter(entry -> !entry.isStatus()).collect(Collectors.toList());
                logger.log("\nError subscribe : " + gson.toJson(filteredList));
                List<Object> list_errorMessage = Arrays.asList(gson.fromJson(gson.toJson(filteredList), ResponseMessage.Message[].class));
                return response.withStatusCode(200).withBody(new ResponseMessage(Json_Request_Error.getCode(), list_errorMessage).convertToJsonString());
            }

            fs_all.add(DynamoDBService.getSubscriptionsList(request.getApp_reg_id()));
            if(! fs_all.get(fs_all.size()-1).isStatus()) {
                logger.log("\nError : " + gson.toJson(fs_all));
                return response.withStatusCode(200).withBody(new ResponseMessage(DynamoDB_Query_Error.getCode(), fs_all.get(fs_all.size() - 1).convertToMessage()).convertToJsonString());
            }

            List<Subscription> arrayList_topicName = (List<Subscription>) fs_all.get(fs_all.size() - 1).getResponse().get("arrayList_channelName");
            fs_all.add(DynamoDBService.getInboxMessageRecord(request, arrayList_topicName));
            if(! fs_all.get(fs_all.size()-1).isStatus()) {
                logger.log("\nError : " + gson.toJson(fs_all));
                return response.withStatusCode(200).withBody(new ResponseMessage(DynamoDB_Query_Error.getCode(), fs_all.get(fs_all.size() - 1).convertToMessage()).convertToJsonString());
            }

            output = new ResponseMessage(200, (List<Object>) fs_all.get(fs_all.size()-1).getResponse().get("inbox_msg"));
        }  else {
            output = new ResponseMessage(Request_Format_Error.getCode(), Request_Format_Error.getError_msg());
            logger.log("Request Error - Message: " + output.getMessage());
        }
        return response.withStatusCode(200).withBody(output.convertToJsonString());
    }
}
