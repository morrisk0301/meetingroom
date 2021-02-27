package meetingroom;

import javax.persistence.*;
import org.springframework.beans.BeanUtils;
import java.util.List;

@Entity
@Table(name="Reserve_table")
public class Reserve {

    @Id
    @GeneratedValue(strategy=GenerationType.AUTO)
    private Long id;
    private String userId;
    private Long roomId;
//    private String startDate;
//    private String startTime;
//    private Integer duration;
    private String status;

    @PostUpdate//usercheck가 되면 status를 업데이트하고 여기서 이벤트를 발생시키자.
    public void onPostUpdate(){
        UserChecked userChecked = new UserChecked();
        BeanUtils.copyProperties(this, userChecked);
        userChecked.publishAfterCommit();
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    @PostPersist
    public void onPostPersist(){
        Reserved reserved = new Reserved();
        BeanUtils.copyProperties(this, reserved);
        reserved.publishAfterCommit();


//        UserChecked userChecked = new UserChecked();
//        BeanUtils.copyProperties(this, userChecked);
//        userChecked.publishAfterCommit();
//
//
//        Canceled canceled = new Canceled();
//        BeanUtils.copyProperties(this, canceled);
//        canceled.publishAfterCommit();


    }
    @PostRemove
    public void onPostRemove(){
        Canceled canceled = new Canceled();
        BeanUtils.copyProperties(this, canceled);
        canceled.publishAfterCommit();
    }



    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }
    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }
    public Long getRoomId() {
        return roomId;
    }

    public void setRoomId(Long roomId) {
        this.roomId = roomId;
    }
//    public String getStartDate() {
//        return startDate;
//    }
//
//    public void setStartDate(String startDate) {
//        this.startDate = startDate;
//    }
//    public String getStartTime() {
//        return startTime;
//    }
//
//    public void setStartTime(String startTime) {
//        this.startTime = startTime;
//    }
//    public Integer getDuration() {
//        return duration;
//    }
//
//    public void setDuration(Integer duration) {
//        this.duration = duration;
//    }




}
