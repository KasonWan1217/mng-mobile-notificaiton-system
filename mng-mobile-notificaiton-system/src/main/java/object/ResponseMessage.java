package object;

import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.List;

public class ResponseMessage {

    private Integer code;
    private List<Object> message;

    public ResponseMessage(Integer code, String fail_msg) {
        this.code = code;
        Message message = new Message(code, fail_msg, fail_msg);
        if (this.message == null)
            this.message = new ArrayList<>();
        this.message.add(message);
    }

    public ResponseMessage(Integer code, Message message) {
        this.code = code;
        if (this.message == null)
            this.message = new ArrayList<>();
        this.message.add(message);
    }

    public ResponseMessage(Integer code, List<Object> message) {
        this.code = code;
        this.message = message;
    }

    public Integer getCode() {
        return code;
    }
    public void setCode(Integer code) {
        this.code = code;
    }

    public List<Object> getMessage() {
        return message;
    }

    public void setMessage(List<Object> message) {
        this.message = message;
    }

    public static class Message {
        private Integer status_code;
        private String datetime;
        private String msg_id;
        private String app_reg_id;
        private String msg_qty;
        private String error_msg;
        private String error_msg_detail;
        private List<InboxMessageRecord> inbox_msg;

        public Message() {}

        public Message(String msg_id, int msg_qty) {
            this.msg_id = msg_id;
            this.msg_qty = String.valueOf(msg_qty);
        }

        public Message(Integer status_code, String error_msg, String error_msg_detail) {
            this.status_code = status_code;
            this.error_msg = error_msg;
            this.error_msg_detail = error_msg_detail;
        }

        public Message(List<InboxMessageRecord> inbox_msg) {
            this.inbox_msg = inbox_msg;
        }

        public Integer getStatus_code() {
            return status_code;
        }

        public void setStatus_code(Integer status_code) {
            this.status_code = status_code;
        }

        public String getMsg_id() {
            return msg_id;
        }

        public void setMsg_id(String msg_id) {
            this.msg_id = msg_id;
        }

        public String getApp_reg_id() {
            return app_reg_id;
        }

        public void setApp_reg_id(String app_reg_id) {
            this.app_reg_id = app_reg_id;
        }

        public String getDatetime() {
            return datetime;
        }

        public void setDatetime(String datetime) {
            this.datetime = datetime;
        }

        public String getMsg_qty() {
            return msg_qty;
        }

        public void setMsg_qty(String msg_qty) {
            this.msg_qty = msg_qty;
        }

        public String getError_msg() {
            return error_msg;
        }

        public void setError_msg(String error_msg) {
            this.error_msg = error_msg;
        }

        public String getError_msg_detail() {
            return error_msg_detail;
        }

        public void setError_msg_detail(String error_msg_detail) {
            this.error_msg_detail = error_msg_detail;
        }

        public List<InboxMessageRecord> getInbox_msg() {
            return inbox_msg;
        }

        public void setInbox_msg(List<InboxMessageRecord> inbox_msg) {
            this.inbox_msg = inbox_msg;
        }
    }

    public String convertToJsonString() {
        return new Gson().toJson(this);
    }

}
