package object.db;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBRangeKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable;
import com.google.gson.Gson;

@DynamoDBTable(tableName = "ApplicationChannel")
public class ApplicationChannel {
    private String channel_name;
    private String app_name;
    private String channel_type;
    private String mobile_type;
    private String create_datetime;

    @DynamoDBHashKey(attributeName="channel_name")
    public String getChannel_name() {
        return channel_name;
    }
    public void setChannel_name(String channel_name) {
        this.channel_name = channel_name;
    }

    @DynamoDBRangeKey(attributeName="app_name")
    public String getApp_name() {
        return app_name;
    }
    public void setApp_name(String app_name) {
        this.app_name = app_name;
    }

    @DynamoDBAttribute(attributeName="channel_type")
    public String getChannel_type() {
        return channel_type;
    }
    public void setChannel_type(String channel_type) {
        this.channel_type = channel_type;
    }

    @DynamoDBAttribute(attributeName="mobile_type")
    public String getMobile_type() {
        return mobile_type;
    }
    public void setMobile_type(String mobile_type) {
        this.mobile_type = mobile_type;
    }

    @DynamoDBAttribute(attributeName="create_datetime")
    public String getCreate_datetime() {
        return create_datetime;
    }
    public void setCreate_datetime(String create_datetime) {
        this.create_datetime = create_datetime;
    }

    public String convertToJsonString() {
        return new Gson().toJson(this);
    }
}
