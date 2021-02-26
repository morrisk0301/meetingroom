package meetingroom;

public class Canceled extends AbstractEvent {

    private Long id;
//    private String userId;
//    private String startDate;
//    private String startTime;
//    private Integer duration;
    private Long roomId;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }
//    public String getUserId() {
//        return userId;
//    }
//
//    public void setUserId(String userId) {
//        this.userId = userId;
//    }
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
    public Long getRoomId() {
        return roomId;
    }

    public void setRoomId(Long roomId) {
        this.roomId = roomId;
    }
}