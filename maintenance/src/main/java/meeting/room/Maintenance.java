package meeting.room;

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
