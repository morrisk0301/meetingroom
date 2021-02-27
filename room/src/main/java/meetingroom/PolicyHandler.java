package meetingroom;

import meetingroom.config.kafka.KafkaProcessor;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.stream.annotation.StreamListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class PolicyHandler{
    @Autowired
    RoomRepository roomRepository;

    @StreamListener(KafkaProcessor.INPUT)
    public void onStringEventListener(@Payload String eventString){

    }

    @StreamListener(KafkaProcessor.INPUT)
    public void wheneverCanceled_(@Payload Canceled canceled){

        if(canceled.isMe()){
            Optional<Room> room = roomRepository.findById(canceled.getRoomId());
            System.out.println("##### listener  : " + canceled.toJson());
            if (room.isPresent()){
                room.get().setStatus("Available");//회의실 예약이 취소되어 예약이 가능해짐.
                roomRepository.save(room.get());
            }
        }
    }
    @StreamListener(KafkaProcessor.INPUT)
    public void wheneverReserved_(@Payload Reserved reserved){

        if(reserved.isMe()){
            Optional<Room> room = roomRepository.findById(reserved.getRoomId());
            System.out.println("##### listener  : " + reserved.toJson());
            if (room.isPresent()){
                room.get().setStatus("Reserved");//회의실이 예약됨.
                roomRepository.save(room.get());
            }
        }
    }
    @StreamListener(KafkaProcessor.INPUT)
    public void wheneverUserChecked_(@Payload UserChecked userChecked){

        if(userChecked.isMe()){
            Optional<Room> room = roomRepository.findById(userChecked.getRoomId());
            System.out.println("##### listener  : " + userChecked.toJson());
            if(room.isPresent()){
                room.get().setStatus("Started");//회의가 시작됨.
                roomRepository.save(room.get());
            }
        }
    }
    @StreamListener(KafkaProcessor.INPUT)
    public void wheneverEnded_(@Payload Ended ended){

        if(ended.isMe()){
            Optional<Room> room = roomRepository.findById(ended.getRoomId());
            System.out.println("##### listener  : " + ended.toJson());
            if(room.isPresent()){
                room.get().setStatus("Available");//회의가 종료됨.
                roomRepository.save(room.get());
            }
        }
    }

}
