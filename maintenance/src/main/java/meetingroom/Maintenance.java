package meetingroom;

import javax.persistence.*;
import org.springframework.beans.BeanUtils;
import java.util.List;

@Entity
@Table(name="Maintenance_table")
public class Maintenance {

    @Id
    @GeneratedValue(strategy=GenerationType.AUTO)
    private Long roomId;
    private String status;

    @PrePersist
    public void onPrePersist(){
        Started started = new Started();
        BeanUtils.copyProperties(this, started);
        started.publishAfterCommit();

        //Following code causes dependency to external APIs
        // it is NOT A GOOD PRACTICE. instead, Event-Policy mapping is recommended.

        meetingroom.external.Reserve reserve = new meetingroom.external.Reserve();
        // mappings goes here
        String result = MaintenanceApplication.applicationContext.getBean(meetingroom.external.ReserveService.class).reserveCheck(reserve);


        Ended ended = new Ended();
        BeanUtils.copyProperties(this, ended);
        ended.publishAfterCommit();


    }


    public Long getRoomId() {
        return roomId;
    }

    public void setRoomId(Long roomId) {
        this.roomId = roomId;
    }
    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }




}
