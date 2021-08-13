import com.amazonaws.services.dynamodbv2.model.ExpectedAttributeValue;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import object.FunctionStatus;
import object.db.AckRecord;
import object.ResponseMessage;
import org.apache.log4j.BasicConfigurator;
import service.DynamoDBService;
import util.CommonUtil;
import util.ErrorMessageUtil;
import util.RequestValidation;

import java.util.*;
import java.util.stream.Collectors;

import static util.ErrorMessageUtil.ErrorMessage.*;

public class StoreAckRecord implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

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
            ArrayList<FunctionStatus> fs_all = new ArrayList<FunctionStatus>();
            AckRecord recordTable = gson.fromJson(input.getBody(), AckRecord.class);
            //Request Validation
            fs_all.addAll(RequestValidation.storeAckRecord_validation(recordTable));
            if (!fs_all.get(fs_all.size() - 1).isStatus())  {
                List<FunctionStatus> filteredList = fs_all.stream().filter(entry -> !entry.isStatus()).collect(Collectors.toList());
                logger.log("\nError subscribe : " + gson.toJson(filteredList));
                List<Object> list_errorMessage = Arrays.asList(gson.fromJson(gson.toJson(filteredList), ResponseMessage.Message[].class));
                return response.withStatusCode(200).withBody(new ResponseMessage(Json_Request_Error.getCode(), list_errorMessage).convertToJsonString());
            }

            if (! CommonUtil.validate_AppRegId(recordTable.getApp_reg_id())) {
                ResponseMessage responseMsg = ErrorMessageUtil.getErrorResponseMessage(AppRegId_Invalid_Error);
                return response.withStatusCode(200).withBody(responseMsg.convertToJsonString());
            }
            recordTable.setRead_timestamp(CommonUtil.getCurrentTime());
            Map expected = new HashMap();
            expected.put("msg_id", new ExpectedAttributeValue(false));
            expected.put("app_reg_id", new ExpectedAttributeValue(false));
            fs_all.add(DynamoDBService.insertData(recordTable, expected));
            if(! fs_all.get(fs_all.size()-1).isStatus()) {
                logger.log("\nError : " + gson.toJson(fs_all));
                return response.withStatusCode(200).withBody(new ResponseMessage(DynamoDB_Insert_Error.getCode(), fs_all.get(fs_all.size() - 1).convertToMessage()).convertToJsonString());
            }

            output = new ResponseMessage(200, new ArrayList());
        }  else {
            output = new ResponseMessage(Request_Format_Error.getCode(), Request_Format_Error.getError_msg());
            logger.log("Request Error - Message: " + output.getMessage());
        }
        return response.withStatusCode(200).withBody(output.convertToJsonString());
    }
}
