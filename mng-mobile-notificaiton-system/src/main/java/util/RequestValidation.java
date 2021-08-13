package util;

import object.FunctionStatus;
import object.db.AckRecord;
import object.db.InboxRecord;
import object.db.SnsAccount;
import object.request.RetrieveInboxRecordRequest;
import object.request.UpdateSubscriptionListRequest;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class RequestValidation extends CommonValidationUtil {

    public static List<FunctionStatus> registerSnsService_validation(SnsAccount request) {
        List<FunctionStatus> list = new ArrayList<>();
        list.add(ckLength_Mandatory(request.getDevice_token(), 1, 250, "Device_token"));
        list.add(ckLength_Mandatory(request.getApp_name(), 1, 30, "App_name"));
        list.add(ckValue_Mandatory(request.getMobile_type(), DBEnumValue.MobileType.values(), "Mobile_type"));
        list.add(ckLength_Mandatory(request.getApp_id(), 2, 2, "App_id"));
        list.add(ckLength_nonMandatory(request.getApp_reg_id(), 20, 20, "App_reg_id"));


        list = list.stream().filter(item -> !item.isStatus()).collect(Collectors.toList());
        if (list.size() < 1)
            list.add(new FunctionStatus(true, null));
        return list;
    }

    public static List<FunctionStatus> retrieveInboxRecord_validation(RetrieveInboxRecordRequest request) {
        List<FunctionStatus> list = new ArrayList<>();
        list.add(ckLength_Mandatory(request.getApp_reg_id(), 20, 20, "App_reg_id"));
        list.add(ckLength_Mandatory(request.getMsg_timestamp(), 14, 14, "Msg_timestamp"));

        list = list.stream().filter(item -> !item.isStatus()).collect(Collectors.toList());
        if (list.size() < 1)
            list.add(new FunctionStatus(true, null));
        return list;
    }

    public static List<FunctionStatus> sendNotification_validation(InboxRecord request) {
        List<FunctionStatus> list = new ArrayList<>();
        list.add(ckLength_Mandatory(request.getMsg_id(), 14, 19, "Msg_id"));
        list.add(ckLength_Mandatory(request.getTarget(), 1, 50, "Target"));
        list.add(ckLength_Mandatory(request.getMsg_timestamp(), 14, 14, "Msg_timestamp"));
        list.add(ckLength_Mandatory(request.getMessage().getTitle(), 1, 50, "Title"));
        list.add(ckLength_Mandatory(request.getMessage().getBody(), 1, 450, "Body"));
        list.add(ckValue_Mandatory(request.getTarget_type(), DBEnumValue.TargetType.values(), "Target_type"));
        list.add(ckValue_Mandatory(request.getBadge(), new int[]{0,1}, "Badge"));

        list.add(ckLength_nonMandatory(request.getMessage().getSub_title(), 1, 100, "Sub_title"));
        list.add(ckLength_nonMandatory(request.getAction_category(), 0, 250, "Action_category"));
        list.add(ckLength_nonMandatory(request.getSound(), 0, 250, "Sound"));
        list.add(ckLength_nonMandatory(request.getPic_url(), 0, 250, "Pic_url"));

        list = list.stream().filter(item -> !item.isStatus()).collect(Collectors.toList());
        if (list.size() < 1)
            list.add(new FunctionStatus(true, null));
        return list;
    }

    public static List<FunctionStatus> storeAckRecord_validation(AckRecord request) {
        List<FunctionStatus> list = new ArrayList<>();
        list.add(ckLength_Mandatory(request.getMsg_id(), 14, 19, "Msg_id"));
        list.add(ckLength_Mandatory(request.getApp_reg_id(), 20, 20, "App_reg_id"));

        list = list.stream().filter(item -> !item.isStatus()).collect(Collectors.toList());
        if (list.size() < 1)
            list.add(new FunctionStatus(true, null));
        return list;
    }

    public static List<FunctionStatus> updateSubscriptionListRequest_validation(UpdateSubscriptionListRequest request) {
        List<FunctionStatus> list = new ArrayList<>();
        list.add(ckLength_Mandatory(request.getApp_reg_id(), 20, 20, "App_reg_id"));
        list.add(ckLength_Mandatory(request.getChannel_name(), 1, 50, "Channel_name"));

        list = list.stream().filter(item -> !item.isStatus()).collect(Collectors.toList());
        if (list.size() < 1)
            list.add(new FunctionStatus(true, null));
        return list;
    }
}
