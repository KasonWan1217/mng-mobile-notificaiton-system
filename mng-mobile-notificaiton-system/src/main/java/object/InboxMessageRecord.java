package object;

import com.google.gson.Gson;

import java.util.Comparator;

public class InboxMessageRecord {
    private String msg_id;
    private String title;
    private String sub_title;
    private String body;
    private String msg_timestamp;

    public InboxMessageRecord(String title, String sub_title, String body, String msg_id, String msg_timestamp) {
        this.msg_id = msg_id;
        this.title = title;
        this.sub_title = sub_title;
        this.body = body;
        this.msg_timestamp = msg_timestamp;
    }

    public void setMessage(InboxMessageRecord inboxMessageRecord) {
        this.title = inboxMessageRecord.getTitle();
        this.sub_title = inboxMessageRecord.getSub_title();
        this.body = inboxMessageRecord.getBody();
    }

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

    public String getMsg_id() {
        return msg_id;
    }

    public void setMsg_id(String msg_id) {
        this.msg_id = msg_id;
    }

    public String getMsg_timestamp() {
        return msg_timestamp;
    }

    public void setMsg_timestamp(String msg_timestamp) {
        this.msg_timestamp = msg_timestamp;
    }

    public static class SortByDate implements Comparator<InboxMessageRecord> {
        @Override
        public int compare(InboxMessageRecord a, InboxMessageRecord b) {
            return b.msg_timestamp.compareTo(a.msg_timestamp);
        }
    }

    public String convertToJsonString() {
        return new Gson().toJson(this);
    }
}
