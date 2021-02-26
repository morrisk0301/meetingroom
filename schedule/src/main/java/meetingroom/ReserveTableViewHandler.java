package meetingroom;

import meetingroom.config.kafka.KafkaProcessor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.stream.annotation.StreamListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

@Service
public class ReserveTableViewHandler {


    @Autowired
    private ReserveTableRepository reserveTableRepository;


    @StreamListener(KafkaProcessor.INPUT)
    public void whenAdded_then_CREATE_1 (@Payload Added added) {
        try {
            if (added.isMe()) {
                // view 객체 생성
                 ReserveTable reserveTable = new ReserveTable();
                // view 객체에 이벤트의 Value 를 set 함
                reserveTable.setRoomId(added.getId());
                reserveTable.setStatus(added.getStatus());
                reserveTable.setFloor(added.getFloor());
                // view 레파지 토리에 save
                reserveTableRepository.save(reserveTable);
            }
        }catch (Exception e){
            e.printStackTrace();
        }
    }
    @StreamListener(KafkaProcessor.INPUT)
    public void whenReserved_then_UPDATE_1 (@Payload Reserved reserved) {
        try {
            if (reserved.isMe()) {
                ReserveTable reserveTable = reserveTableRepository.findByRoomId(reserved.getRoomId());
                System.out.println("$$$ reservedTable View를 위한 reserved.toJson() : " + reserved.toJson());
                reserveTable.setReserveId(reserved.getId());
                reserveTable.setStatus("unavailable");
                reserveTable.setUserId(reserved.getUserId());
                reserveTable.setDuration(reserved.getDuration());
                reserveTable.setStartDate(reserved.getStartDate());
                reserveTable.setStartTime(reserved.getStartTime());
                // view 객체에 이벤트의 eventDirectValue 를 set 함
                // view 레파지 토리에 save
                reserveTableRepository.save(reserveTable);
            }
        }catch (Exception e){
            e.printStackTrace();
        }
    }
    @StreamListener(KafkaProcessor.INPUT)
    public void whenReserved_then_UPDATE_2 (@Payload Canceled canceled) {
        try {
            if (canceled.isMe()) {
                ReserveTable reserveTable= reserveTableRepository.findByReserveId(canceled.getId());
                //System.out.println("$$$ reservedTable View를 위한 reserved.toJson() : " + reserved.toJson());
                reserveTable.setReserveId(null);
                reserveTable.setStatus(null);
                reserveTable.setUserId(null);
                reserveTable.setDuration(null);
                reserveTable.setStartDate(null);
                reserveTable.setStartTime(null);
                // view 객체에 이벤트의 eventDirectValue 를 set 함
                // view 레파지 토리에 save
                reserveTableRepository.save(reserveTable);
            }
        }catch (Exception e){
            e.printStackTrace();
        }
    }
    @StreamListener(KafkaProcessor.INPUT)
    public void whenReserved_then_UPDATE_3 (@Payload Ended ended) {
        try {
            if (ended.isMe()) {
                ReserveTable reserveTable = reserveTableRepository.findByReserveId(ended.getReserveId());
                //System.out.println("$$$ reservedTable View를 위한 reserved.toJson() : " + reserved.toJson());

                reserveTable.setReserveId(null);
                reserveTable.setStatus(null);
                reserveTable.setUserId(null);
                reserveTable.setDuration(null);
                reserveTable.setStartDate(null);
                reserveTable.setStartTime(null);
                // view 객체에 이벤트의 eventDirectValue 를 set 함
                // view 레파지 토리에 save
                reserveTableRepository.save(reserveTable);

            }
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    /*@StreamListener(KafkaProcessor.INPUT)
    public void whenDeleted_then_DELETE_1(@Payload Deleted deleted) {
        try {
            if (deleted.isMe()) {
                // view 레파지 토리에 삭제 쿼리
                reserveTableRepository.deleteByRoomId(deleted.getId());
            }
        }catch (Exception e){
            e.printStackTrace();
        }
    }*/
}