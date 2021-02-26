package meetingroom;

import javax.persistence.*;
import org.springframework.beans.BeanUtils;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;

import java.util.List;

@Entity
@Table(name="Conference_table")
public class Conference {

    @Id
    @GeneratedValue(strategy=GenerationType.AUTO)
    private Long id;
    private Long roomId;
    private String userId;
    private Long reserveId;

    @PrePersist
    public void onPrePersist(){//pre로 바꺼야하나;;
        /*Started started = new Started();
        BeanUtils.copyProperties(this, started);
        started.publishAfterCommit();*/

        //Following code causes dependency to external APIs
        // it is NOT A GOOD PRACTICE. instead, Event-Policy mapping is recommended.

        meetingroom.external.Reserve reserve = new meetingroom.external.Reserve();
        // mappings goes here
        reserve.setId(reserveId);
        reserve.setUserId(userId);
        reserve.setRoomId(roomId);
        String result = ConferenceApplication.applicationContext.getBean(meetingroom.external.ReserveService.class).userCheck(reserve);

        if(result.equals("valid")){
            System.out.println("Success!");
        }
        else{
            /// usercheck가 유효하지 않을 때 강제로 예외 발생
                System.out.println("FAIL!! InCorrect User or Incorrect Resevation");
                Exception ex = new Exception();
                ex.notify();
            //ConferenceApplication.applicationContext.getBean(meetingroom.ConferenceRepository.class).deleteById(this.id);//userid가 예약한 userid가 아니면 생성했던 conference를 삭제.

        }
    }
    @PreRemove
    public void onPreRemove(){
        Ended ended = new Ended();
        BeanUtils.copyProperties(this, ended);
        ended.publishAfterCommit();
    }


    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }
    public Long getRoomId() {
        return roomId;
    }

    public void setRoomId(Long roomId) {
        this.roomId = roomId;
    }
    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }
    public Long getReserveId() {
        return reserveId;
    }

    public void setReserveId(Long reserveId) {
        this.reserveId = reserveId;
    }




}
