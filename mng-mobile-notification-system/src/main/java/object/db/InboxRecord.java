package object.db;

import com.amazonaws.services.dynamodbv2.datamodeling.*;
import com.google.gson.Gson;

import java.util.List;

@DynamoDBTable(tableName = "InboxRecord")
public class InboxRecord {
    private String msg_id;
    private List<String> sns_msg_id;
    private String msg_timestamp;
    private String target;
    private String target_type;
    private Message message;
    private String action_category;
    private int badge;
    private String sound;
    private String pic_url;
    private boolean direct_msg;
    private Integer msg_qty;
    private String create_datetime;
    private Remark supplementary_field;

    //dynamoDB ignore.
    private int notification_id;

    public InboxRecord(String json) {
        InboxRecord request = new Gson().fromJson(json, InboxRecord.class);
        this.msg_id = request.getMsg_id();
        this.message = request.getMessage();
        this.target = request.getTarget();
        this.target_type = request.getTarget_type();
        this.action_category = request.getAction_category();
        this.badge = request.getBadge();
        this.sound = request.getSound();
        this.msg_timestamp = request.getMsg_timestamp();
        this.pic_url = request.getPic_url();
        this.setDirect_msg(request.isDirect_msg());
    }

    @DynamoDBHashKey(attributeName="msg_id")
    public String getMsg_id() {
        return msg_id;
    }
    public void setMsg_id(String msg_id) {
        this.msg_id = msg_id;
    }

    @DynamoDBAttribute(attributeName="sns_msg_id")
    public List<String> getSns_msg_id() {
        return sns_msg_id;
    }
    public void setSns_msg_id(List<String> sns_msg_id) {
        this.sns_msg_id = sns_msg_id;
    }

    @DynamoDBAttribute(attributeName="msg_timestamp")
    public String getMsg_timestamp() {
        return msg_timestamp;
    }
    public void setMsg_timestamp(String msg_timestamp) {
        this.msg_timestamp = msg_timestamp;
    }

    @DynamoDBAttribute(attributeName="target")
    public String getTarget() {
        return target;
    }
    public void setTarget(String target) {
        this.target = target;
    }

    @DynamoDBAttribute(attributeName="target_type")
    public String getTarget_type() {
        return target_type;
    }
    public void setTarget_type(String target_type) {
        this.target_type = target_type;
    }

    @DynamoDBAttribute(attributeName="message")
    public Message getMessage() {
        return message;
    }
    public void setMessage(Message message) {
        this.message = message;
    }

    @DynamoDBAttribute(attributeName="action_category")
    public String getAction_category() {
        return action_category;
    }
    public void setAction_category(String action_category) {
        this.action_category = action_category;
    }

    @DynamoDBAttribute(attributeName="badge")
    public int getBadge() {
        return badge;
    }
    public void setBadge(int badge) {
        this.badge = badge;
    }

    @DynamoDBAttribute(attributeName="sound")
    public String getSound() {
        return sound;
    }
    public void setSound(String sound) {
        this.sound = sound;
    }

    @DynamoDBAttribute(attributeName="pic_url")
    public String getPic_url() {
        return pic_url;
    }
    public void setPic_url(String pic_url) {
        this.pic_url = pic_url;
    }

    @DynamoDBIgnore
    public boolean isDirect_msg() {
        return direct_msg;
    }
    public void setDirect_msg(boolean direct_msg) {
        this.direct_msg = direct_msg;
    }

    @DynamoDBAttribute(attributeName="msg_qty")
    public Integer getMsg_qty() {
        return msg_qty;
    }
    public void setMsg_qty(Integer msg_qty) {
        this.msg_qty = msg_qty;
    }

    @DynamoDBAttribute(attributeName="create_datetime")
    public String getCreate_datetime() {
        return create_datetime;
    }
    public void setCreate_datetime(String create_datetime) {
        this.create_datetime = create_datetime;
    }

    @DynamoDBAttribute(attributeName="supplementary_field")
    public Remark getSupplementary_field() {
        return supplementary_field;
    }
    public void setSupplementary_field(Remark supplementary_field) {
        this.supplementary_field = supplementary_field;
    }

    @DynamoDBDocument
    public class Message {
        private String title;
        private String sub_title;
        private String body;

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getSub_title() {
            return sub_title;
        }

        public void setSub_title(String sub_title) {
            this.sub_title = sub_title;
        }

        public String getBody() {
            return body;
        }

        public void setBody(String body) {
            this.body = body;
        }

        public String convertToJsonString() {
            return new Gson().toJson(this);
        }
    }

    @DynamoDBDocument
    public class Remark {
        public String convertToJsonString() {
            return new Gson().toJson(this);
        }
    }

    @DynamoDBIgnore
    public int getNotification_id() {
        return notification_id;
    }
    public void setNotification_id(int notification_id) {
        this.notification_id = notification_id;
    }

    public String convertToJsonString() {
        return new Gson().toJson(this);
    }
}
