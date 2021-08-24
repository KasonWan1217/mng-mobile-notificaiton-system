package service;

import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import object.FunctionStatus;
import object.PushMessage;
import object.db.InboxRecord;
import object.db.SnsAccount.Subscription;
import software.amazon.awssdk.auth.credentials.EnvironmentVariableCredentialsProvider;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.*;
import util.DBEnumValue.ArnType;
import util.DBEnumValue.TargetType;

import java.util.HashMap;
import java.util.List;
import java.util.function.Consumer;

import static com.amazonaws.services.lambda.runtime.LambdaRuntime.getLogger;

public class SNSNotificationService {
    static final LambdaLogger logger = getLogger();
    private final static SnsClient snsClient;
    static {
        snsClient = SnsClient.builder()
                .region(Region.AP_SOUTHEAST_1)
                .credentialsProvider(EnvironmentVariableCredentialsProvider.create())
                .httpClient(ApacheHttpClient.builder().build())
                .build();
    }

    public static FunctionStatus register(String device_token, String platformApplicationArn) {
        try {
            Consumer<software.amazon.awssdk.services.sns.model.CreatePlatformEndpointRequest.Builder> consumer = builder -> {
                builder.platformApplicationArn(platformApplicationArn);
                builder.token(device_token);
                builder.customUserData("LAMBDA");
            };
            CreatePlatformEndpointResponse response = snsClient.createPlatformEndpoint(consumer);
            logger.log("\nregister - CreatePlatformEndpointResponse response: " + response.endpointArn() + "\n");
            HashMap<String, Object> result = new HashMap<>();
            result.put("endpointArn", response.endpointArn());
            return new FunctionStatus(true, result);
        } catch (SnsException e) {
            logger.log("\nregister Error: " + e.getMessage() + "\n");
            return new FunctionStatus(false, e.statusCode(), e.awsErrorDetails().errorMessage(), e.getMessage());
        }
    }

    public static FunctionStatus unregister(String endpointArn) {
        logger.log("\nunRegister: " + endpointArn + "\n");
        try {
            Consumer<software.amazon.awssdk.services.sns.model.DeleteEndpointRequest.Builder> consumer = builder -> builder.endpointArn(endpointArn);
            DeleteEndpointResponse response = snsClient.deleteEndpoint(consumer);
            logger.log("\nunRegister - DeleteEndpointResponse: " + response.toString() + "\n");
            return new FunctionStatus(true, null);
        } catch (SnsException e) {
            logger.log("\nunRegister Error: " + e.getMessage() + "\n");
            return new FunctionStatus(false, e.statusCode(), e.awsErrorDetails().errorMessage(), e.getMessage());
        }
    }

    public static FunctionStatus subscribe(String endpointArn, String topic) {
        try {
            SubscribeRequest request = SubscribeRequest.builder()
                    .protocol("application")
                    .endpoint(endpointArn)
                    .returnSubscriptionArn(true)
                    .topicArn(topic)
                    .build();
            SubscribeResponse response = snsClient.subscribe(request);
            logger.log("\nSubscribeResponse : " + response.sdkHttpResponse().statusCode() + "\n");
            logger.log("\nSubscribeResponse : " + response.toString() + "\n");
            HashMap<String, Object> result = new HashMap<>();
            result.put("subscriptionArn", response.subscriptionArn());
            return new FunctionStatus(true, result);
        } catch (SnsException e) {
            logger.log("\nSubscribe Error: " + e.getMessage() + "\n");
            return new FunctionStatus(false, e.statusCode(), e.awsErrorDetails().errorMessage(), e.getMessage());
        }
    }

    public static FunctionStatus unsubscribe(String subscriptionArn) {
        logger.log("\nunSubscribe subscriptionArn: " + subscriptionArn + "\n");
        try {
            UnsubscribeRequest request = UnsubscribeRequest.builder()
                    .subscriptionArn(subscriptionArn)
                    .build();
            UnsubscribeResponse response = snsClient.unsubscribe(request);
            logger.log("\nUnsubscribeResponse : " + response.sdkHttpResponse().statusCode() + "\n");
            logger.log("\nUnsubscribeResponse : " + response.toString() + "\n");
            return new FunctionStatus(true, null);
        } catch (SnsException e) {
            logger.log("\nUnregister Error: " + e.getMessage() + "\n");
            return new FunctionStatus(false, e.statusCode(), e.awsErrorDetails().errorMessage(), e.getMessage());
        }
    }

    public static FunctionStatus resetAccount(List<Subscription> topicSubscriptions) {
        logger.log("\nsubscribe topicSubscriptions.size(): " + topicSubscriptions.size() + "\n");
        for (Subscription subscription: topicSubscriptions) {
            logger.log("\nsubscribe : " + subscription.convertToJsonString() + "\n");
            FunctionStatus responseMessage = (ArnType.Platform.toString().equals((subscription.getChannel_type()))) ?
                                                                                    unregister(subscription.getArn()):
                                                                                    unsubscribe(subscription.getArn());
            if (! responseMessage.isStatus()) {
                logger.log("\nresetAccount Error: " + responseMessage.convertToJsonString() + "\n");
                return responseMessage;
            }
        }
        logger.log("\nresetAccount Success!\n");
        return new FunctionStatus(true, null);
    }

    public FunctionStatus publishNotification(InboxRecord recordTable, String arn) {
        PushMessage pushMessage = new PushMessage(recordTable);
        String message = new Gson().toJson(pushMessage);

        FunctionStatus response = (TargetType.Group.toString().equals(recordTable.getTarget_type())) ?
                pubTopic(message, arn):
                pubTarget(message, arn);
        snsClient.close();
        return response;
    }

    private static FunctionStatus pubTopic(String message, String topicArn) {
        logger.log("SNSNotificationService.pubTopic - Start "+ topicArn);
        try {
            PublishRequest request = PublishRequest.builder()
                    .topicArn(topicArn)
                    .messageStructure("json")
                    .message(message).build();
            logger.log("SNSNotificationService.pubTopic - Sending" );
            PublishResponse result = snsClient.publish(request);
            logger.log(result.messageId() + " Message sent. Status was " + result.sdkHttpResponse().statusCode());
            Consumer<ListSubscriptionsByTopicRequest.Builder> listSubscriptionsByTopicRequest = builder -> builder.topicArn(topicArn);
            int msg_qty = snsClient.listSubscriptionsByTopic(listSubscriptionsByTopicRequest).subscriptions().size();
            logger.log("Topic pushMsg_QTY: " + msg_qty);

            HashMap<String, Object> response = new HashMap<>();
            response.put("msg_id", result.messageId());
            response.put("msg_qty", msg_qty);
            return new FunctionStatus(true, response);
        } catch (SnsException e) {
            logger.log("\npubTopic Error: " + e.getMessage() + "\n");
            return new FunctionStatus(false, e.statusCode(), e.awsErrorDetails().errorMessage(), e.getMessage());
        }
    }

    private static FunctionStatus pubTarget(String message, String targetArn) {
        logger.log("SNSNotificationService.pubTarget -  Start :" + targetArn);
        try {
            new GsonBuilder().setPrettyPrinting().serializeNulls();
            PublishRequest request = PublishRequest.builder()
                    .targetArn(targetArn)
                    .messageStructure("json")
                    .message(message).build();
            logger.log("SNSNotificationService.pubTarget - Sending");
            PublishResponse result = snsClient.publish(request);
            logger.log(result.messageId() + " Message sent. Status was " + result.sdkHttpResponse().statusCode());

            HashMap<String, Object> response = new HashMap<>();
            response.put("msg_id", result.messageId());
            response.put("msg_qty", 1);
            return new FunctionStatus(true, response);
        } catch (SnsException e) {
            logger.log("\npubTarget Error: " + e.getMessage() + "\n");
            return new FunctionStatus(false, e.statusCode(), e.awsErrorDetails().errorMessage(), e.getMessage());
        }
    }
}
