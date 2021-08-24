package object.request;

import com.google.gson.Gson;

public class RetrieveInboxRecordRequest {
    private String app_reg_id;
    private String msg_timestamp;

    public String getApp_reg_id() {
        return app_reg_id;
    }

    public void setApp_reg_id(String app_reg_id) {
        this.app_reg_id = app_reg_id;
    }

    public String getMsg_timestamp() {
        return msg_timestamp;
    }

    public void setMsg_timestamp(String msg_timestamp) {
        this.msg_timestamp = msg_timestamp;
    }

    public String convertToJsonString() {
        return new Gson().toJson(this);
    }
}
