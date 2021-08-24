package object.db;

import com.amazonaws.services.dynamodbv2.datamodeling.*;
import com.google.gson.Gson;
import util.CommonUtil;

@DynamoDBTable(tableName = "AckRecord")
public class AckRecord {
    private String msg_id;
    private String app_reg_id;
    private String read_timestamp;
    private Remark supplementary_field;

    @DynamoDBHashKey(attributeName="msg_id")
    public String getMsg_id() {
        return msg_id;
    }
    public void setMsg_id(String msg_id) {
        this.msg_id = msg_id;
    }

    @DynamoDBRangeKey(attributeName="app_reg_id")
    public String getApp_reg_id() {
        return app_reg_id;
    }
    public void setApp_reg_id(String app_reg_id) {
        this.app_reg_id = app_reg_id;
    }

    @DynamoDBAttribute(attributeName="read_timestamp")
    public String getRead_timestamp() {
        return read_timestamp;
    }
    public void setRead_timestamp(String read_timestamp) {
        this.read_timestamp = read_timestamp;
    }

    @DynamoDBAttribute(attributeName="supplementary_field")
    public Remark getSupplementary_field() {
        return supplementary_field;
    }
    public void setSupplementary_field(Remark supplementary_field) {
        this.supplementary_field = supplementary_field;
    }

    @DynamoDBDocument
    public class Remark {
        public String convertToJsonString() {
            return new Gson().toJson(this);
        }
    }

    public String convertToJsonString() {
        return new Gson().toJson(this);
    }
}
