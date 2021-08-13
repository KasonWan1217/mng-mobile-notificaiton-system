package service;

import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBSaveExpression;
import com.amazonaws.services.dynamodbv2.document.DynamoDB;
import com.amazonaws.services.dynamodbv2.document.Index;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.document.Item;
import com.amazonaws.services.dynamodbv2.document.ItemCollection;
import com.amazonaws.services.dynamodbv2.document.QueryOutcome;
import com.amazonaws.services.dynamodbv2.document.Table;
import com.amazonaws.services.dynamodbv2.document.spec.QuerySpec;
import com.amazonaws.services.dynamodbv2.document.utils.ValueMap;
import com.amazonaws.services.lambda.runtime.LambdaLogger;

import com.google.gson.Gson;

import com.google.gson.GsonBuilder;
import object.FunctionStatus;
import object.db.ApplicationChannel;
import object.db.InboxRecord;
import object.InboxMessageRecord;
import object.db.SnsAccount;
import object.db.SnsAccount.Subscription;
import object.request.RetrieveInboxRecordRequest;
import util.DBEnumValue;
import util.ErrorMessageUtil;

import java.util.*;
import java.util.stream.Collectors;

import static com.amazonaws.services.lambda.runtime.LambdaRuntime.getLogger;
import static util.ErrorMessageUtil.ErrorMessage.*;

public class DynamoDBService {
    private static final AmazonDynamoDB client = AmazonDynamoDBClientBuilder.standard().build();
    private static final DynamoDB dynamoDB = new DynamoDB(client);
    private static final LambdaLogger logger = getLogger();

    public static FunctionStatus updateData(Object obj) {
        try {
            DynamoDBMapper mapper = new DynamoDBMapper(client);
            mapper.save(obj);
            logger.log("Update Record Complete.");
            return new FunctionStatus(true, null);
        } catch (AmazonServiceException ase) {
            logger.log("Could not complete operation");
            return new FunctionStatus(false, ase.getStatusCode(), ase.getMessage(), ase.getErrorMessage());
        } catch (AmazonClientException ace) {
            logger.log("Internal error occurred communicating with DynamoDB");
            logger.log("Error Message:  " + ace.getMessage());
            return new FunctionStatus(false, AmazonClient_Error.getCode(), ace.getMessage());
        }
    }

    public static FunctionStatus deleteData(Object obj) {
        try {
            DynamoDBMapper mapper = new DynamoDBMapper(client);
            mapper.delete(obj);
            logger.log("Delete Record Complete.");
            return new FunctionStatus(true, null);
        } catch (AmazonServiceException ase) {
            logger.log("Could not complete operation");
            return new FunctionStatus(false, ase.getStatusCode(), ase.getMessage(), ase.getErrorMessage());
        } catch (AmazonClientException ace) {
            logger.log("Internal error occurred communicating with DynamoDB");
            logger.log("Error Message:  " + ace.getMessage());
            return new FunctionStatus(false, AmazonClient_Error.getCode(), ace.getMessage());
        }
    }

    public static FunctionStatus insertData(Object obj, Map expected) {
        try {
            DynamoDBMapper mapper = new DynamoDBMapper(client);
            DynamoDBSaveExpression saveExpression = new DynamoDBSaveExpression();
            saveExpression.setExpected(expected);
            mapper.save(obj, saveExpression);
            logger.log("Insert Record Complete.");
            return new FunctionStatus(true, null);
        } catch (AmazonServiceException ase) {
            logger.log("Could not complete operation");
            return new FunctionStatus(false, ase.getStatusCode(), ase.getMessage(), ase.getErrorMessage());
        } catch (AmazonClientException ace) {
            logger.log("Internal error occurred communicating with DynamoDB");
            logger.log("Error Message:  " + ace.getMessage());
            return new FunctionStatus(false, AmazonClient_Error.getCode(), ace.getMessage());
        }
    }

    public static FunctionStatus getSubscriptionsList(String app_reg_id) {
        FunctionStatus functionStatus = getSnsAccount_AppRegId(app_reg_id);
        SnsAccount snsAccount = (SnsAccount) functionStatus.getResponse().get("snsAccount");
        if(! functionStatus.isStatus()) {
            logger.log("\nError getSubscriptionsList: " + new Gson().toJson(functionStatus));
            return functionStatus;
        } else if (snsAccount == null){
            return ErrorMessageUtil.getFunctionStatus(AppRegId_Null_Error);
        }

        List<Subscription> arrayList_channelName = snsAccount.getSubscriptions();
        if (arrayList_channelName == null || arrayList_channelName.size() == 0) {
            return ErrorMessageUtil.getFunctionStatus(AppRegId_Inactive_Error);
        }
        HashMap<String, Object> rs = new HashMap<>();
        rs.put("arrayList_channelName", arrayList_channelName);
        return new FunctionStatus(true, rs);
    }

    public static FunctionStatus getInboxMessageRecord(RetrieveInboxRecordRequest request, List<Subscription> arrayList_channelName) {
        List<InboxMessageRecord> inbox_msg = new ArrayList<>();
        arrayList_channelName.add(new Subscription(request.getApp_reg_id(),"","",""));
        for (Subscription subscription : arrayList_channelName) {
            FunctionStatus temp_fs = getInboxMessageRecord(subscription.getChannel_name(), request.getMsg_timestamp());
            if (! temp_fs.isStatus()) {
                logger.log("\nError : " + new GsonBuilder().setPrettyPrinting().create().toJson(temp_fs));
                return temp_fs;
            }
            List<InboxMessageRecord> list = (List<InboxMessageRecord>) temp_fs.getResponse().get("InboxMessageRecord");
            inbox_msg.addAll(list);
            logger.log("Topic: " +subscription.getChannel_name()+" \nSize: " + list.size());
        }

        inbox_msg.sort(new InboxMessageRecord.SortByDate());
        inbox_msg = inbox_msg.stream().limit(7).collect(Collectors.toList());
        logger.log("InboxMessageRecord List 2 Size: " + inbox_msg.size());

        HashMap<String, Object> rs = new HashMap<>();
        rs.put("inbox_msg", inbox_msg);
        return new FunctionStatus(true, rs);
    }

    public static FunctionStatus getSnsAccount_AppRegId(String app_reg_id) {
        try {
            Table table = dynamoDB.getTable("SnsAccount");
            Index index = table.getIndex("AppRegId-ActiveStatus-GSI");

            ItemCollection<QueryOutcome> items = null;
            QuerySpec querySpec = new QuerySpec();

            if ("AppRegId-ActiveStatus-GSI".equals(index.getIndexName())) {
                querySpec.withKeyConditionExpression("app_reg_id = :v1 AND active_status =:v2")
                        .withValueMap(new ValueMap()
                                .withString(":v1", app_reg_id)
                                .withString(":v2", DBEnumValue.Status.Success.toString())
                        ).setMaxResultSize(1);
                items = index.query(querySpec);
                if (items == null) {
                    logger.log("getSnsAccount : item is null");
                } else {
                    logger.log("getSnsAccount : item is not null");
                }
            }

            assert items != null;
            Iterator<Item> iterator = items.iterator();
            SnsAccount snsAccount = null;
            while (iterator.hasNext()) {
                Item item = iterator.next();
                logger.log("getSnsAccount: " + item.toJSONPretty());
                snsAccount = new Gson().fromJson(item.toJSON(), SnsAccount.class);
            }
            HashMap<String, Object> rs = new HashMap<>();
            rs.put("snsAccount", snsAccount);
            return new FunctionStatus(true, rs);
        } catch (AmazonServiceException ase) {
            logger.log("Could not complete operation");
            return new FunctionStatus(false, ase.getStatusCode(), ase.getMessage(), ase.getErrorMessage());
        } catch (AmazonClientException ace) {
            logger.log("Internal error occurred communicating with DynamoDB");
            logger.log("Error Message:  " + ace.getMessage());
            return new FunctionStatus(false, AmazonClient_Error.getCode(), ace.getMessage());
        }
    }

    public static FunctionStatus getSnsAccount_DeviceToken(String device_token) {
        try {
            Table table = dynamoDB.getTable("SnsAccount");
            Index index = table.getIndex("DeviceToken-ActiveStatus-GSI");

            ItemCollection<QueryOutcome> items = null;
            QuerySpec querySpec = new QuerySpec();

            if ("DeviceToken-ActiveStatus-GSI".equals(index.getIndexName())) {
                querySpec.withKeyConditionExpression("device_token = :v1 AND active_status = :v2")
                        .withValueMap(new ValueMap()
                                .withString(":v1", device_token)
                                .withString(":v2", DBEnumValue.Status.Success.toString())
                        ).setMaxResultSize(1);
                items = index.query(querySpec);
                if (items == null) {
                    logger.log("getActiveSnsAccount : item is null");
                } else {
                    logger.log("getActiveSnsAccount : item is not null");
                }
            }

            assert items != null;
            Iterator<Item> iterator = items.iterator();
            SnsAccount snsAccount = null;
            while (iterator.hasNext()) {
                Item item = iterator.next();
                logger.log("getSnsAccount: " + item.toJSONPretty());
                snsAccount = new Gson().fromJson(item.toJSON(), SnsAccount.class);
            }
            HashMap<String, Object> rs = new HashMap<>();
            rs.put("snsAccount", snsAccount);
            return new FunctionStatus(true, rs);
        } catch (AmazonServiceException ase) {
            logger.log("Could not complete operation");
            return new FunctionStatus(false, ase.getStatusCode(), ase.getMessage(), ase.getErrorMessage());
        } catch (AmazonClientException ace) {
            logger.log("Internal error occurred communicating with DynamoDB");
            logger.log("Error Message:  " + ace.getMessage());
            return new FunctionStatus(false, AmazonClient_Error.getCode(), ace.getMessage());
        }

    }

    public static FunctionStatus getChannelList(String app_name, String mobile_type) {
        try {
            Table table = dynamoDB.getTable("ApplicationChannel");
            Index index = table.getIndex("AppName-MobileType-GSI");

            ItemCollection<QueryOutcome> items = null;
            QuerySpec querySpec = new QuerySpec();

            if ("AppName-MobileType-GSI".equals(index.getIndexName())) {
                querySpec.withKeyConditionExpression("app_name = :v1 AND mobile_type = :v2")
                        .withValueMap(new ValueMap()
                                .withString(":v1", app_name)
                                .withString(":v2", mobile_type)
                        );
                items = index.query(querySpec);
                if (items == null) {
                    logger.log("getChannel List : item is null");
                } else {
                    logger.log("getChannel List : item is not null");
                }
            }

            List<ApplicationChannel> list = new ArrayList<>();
            assert items != null;


            for (Item item : items) {
                ApplicationChannel tempObj = new Gson().fromJson(item.toJSON(), ApplicationChannel.class);
                list.add(tempObj);
            }
            logger.log("ApplicationChannel List : " + new Gson().toJson(list));

            HashMap<String, Object> rs = new HashMap<>();
            if (list.size() > 0) {
                rs.put("ApplicationChannelList", list);
                return new FunctionStatus(true, rs);
            } else {
                return ErrorMessageUtil.getFunctionStatus(PlatformName_Null_Error);
            }
        } catch (AmazonServiceException ase) {
            logger.log("Could not complete operation");
            return new FunctionStatus(false, ase.getStatusCode(), ase.getMessage(), ase.getErrorMessage());
        } catch (AmazonClientException ace) {
            logger.log("Internal error occurred communicating with DynamoDB");
            logger.log("Error Message:  " + ace.getMessage());
            return new FunctionStatus(false, AmazonClient_Error.getCode(), ace.getMessage());
        }

    }

    private static FunctionStatus getInboxMessageRecord(String target, String msg_timestamp) {
        try {
            Table table = dynamoDB.getTable("InboxRecord");
            Index index = table.getIndex("Target-MsgTimestamp-GSI");

            ItemCollection<QueryOutcome> items = null;
            QuerySpec querySpec = new QuerySpec();

            if ("Target-MsgTimestamp-GSI".equals(index.getIndexName())) {
                querySpec.withKeyConditionExpression("target = :v1 AND msg_timestamp > :v2")
                        .withValueMap(new ValueMap()
                                .withString(":v1", target)
                                .withString(":v2", msg_timestamp)
                        ).withScanIndexForward(false).setMaxResultSize(7);
                items = index.query(querySpec);
                if (items == null) {
                    logger.log("getInboxMessageRecord : item is null");
                } else {
                    logger.log("getInboxMessageRecord : item is not null");
                }
            }

            List<InboxMessageRecord> list = new ArrayList<>();
            assert items != null;

            for (Item item : items) {
                String temp = item.toJSONPretty();
                logger.log("getInboxMessageRecord: " + temp);
                InboxRecord inboxRecord = new Gson().fromJson(temp, InboxRecord.class);
                InboxMessageRecord inboxMessageRecord = new Gson().fromJson(inboxRecord.convertToJsonString(), InboxMessageRecord.class);
                inboxMessageRecord.setMessage(new Gson().fromJson(inboxRecord.getMessage().convertToJsonString(), InboxMessageRecord.class));
                list.add(inboxMessageRecord);
            }
            logger.log("getInboxMessageRecord Size: " + list.size());
            logger.log("getInboxMessageRecord : " + new Gson().toJson(list));

            HashMap<String, Object> rs = new HashMap<>();
            rs.put("InboxMessageRecord", list);
            return new FunctionStatus(true, rs);
        } catch (AmazonServiceException ase) {
            logger.log("Could not complete operation");
            return new FunctionStatus(false, ase.getStatusCode(), ase.getMessage(), ase.getErrorMessage());
        } catch (AmazonClientException ace) {
            logger.log("Internal error occurred communicating with DynamoDB");
            logger.log("Error Message:  " + ace.getMessage());
            return new FunctionStatus(false, AmazonClient_Error.getCode(), ace.getMessage());
        }
    }

}
