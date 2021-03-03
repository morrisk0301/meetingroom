# meetingroom

# 서비스 시나리오
## 기능적 요구사항
1. 기본적으로 선착순으로 당일 사용할 회의실 예약을 선점하도록 혹은 8시 ~ 9시 50분에 대한 회의실 예약은 8시에, 10시 ~ 11시 50분에 대한 회의실 예약은 10시에(그 뒷 시간도 일정한 시간에) 진행하는 선점 회의실 예약 시나리오를 작성했다.
2. 해당 회의실이 예약 중이거나 회의 중이라면 예약을 할 수 없고, 예약 취소나 회의 종료가 일어났을 때 예약을 할 수 있도록 생각했다.(티켓팅 처럼) 
3. 회원이 회의실에 대한 예약을 한다.
4. 회원이 예약한 회의실에 대해 회의 시작을 요청한다.
5. 예약한 사람이면 해당 회의를 시작할 수 있도록 한다.
6. 예약한 사람이 아니면 회의를 시작할 수 없다.
7. 예약한 회의에 대해 예약 취소를 요청할 수 있다.
8. 시작했던 회의를 종료한다.
9. 회의실 정비가 필요한 경우 관리자가 회의실 정비 요청을 할 수 있다.
10. 회의실 정비는 예약이 없는 회의실에 대해서만 진행할 수 있다.
11. 정비가 종료 후 관리자가 회의실 정비를 종료하여 예약 가능한 상태로 바꾼다.

## 비기능적 요구사항
1. 트랜잭션
  - 회의 시작을 요청할 때, 회의를 예약한 사람이 아니라면 회의를 시작하지 못하게 한다.(Sync 호출)
2. 장애격리
  - 회의실 시스템이 수행되지 않더라도 예약취소, 사용자 확인, 회의 종료는 365일 24시간 받을 수 있어야 한다. (Async 호출)
3. 성능
  - 회의실 현황에 대해 예약 상황을 별도로 확인할 수 있어야 한다.(CQRS)

# 체크포인트
https://workflowy.com/s/assessment/qJn45fBdVZn4atl3

## EventStorming 결과
### 완성된 1차 모형
<img width="1033" alt="스크린샷 2021-03-03 오후 5 15 08" src="https://user-images.githubusercontent.com/33116855/109774918-2f699100-7c44-11eb-8182-801afd2543c3.png">


### 1차 완성본에 대한 기능적/비기능적 요구사항을 커버하는지 검증
![그림1](https://user-images.githubusercontent.com/33116855/109774925-309abe00-7c44-11eb-8eaf-e314e9ff63bf.png)


    
    - 회의실이 등록이 된다. (8)
    - 회원이 회의실을 예약을 한다. (3 -> 7)
    - 회원이 예약한 회의실에 대해 회의 시작을 요청한다.
      - 예약한 사람이면 회의를 시작한다. (1 -> 5 -> 7)
      - 예약한 사람이 아니면 회의 시작을 못한다. (1)
    - 회원이 예약한 회의실을 예약 취소한다. (4 -> 7)
    - 시작했던 회의를 종료한다. (2 -> 7)
    - 관리자가 회의실 정비를 요청한다.
      - 예약된 회의실일 경우 정비하지 못한다. (9 -> 6)
      - 예약되지 않은 회의실의 경우 정비한다. (9 -> 6, 9 -> 7)
    - 관리자가 회의실 정비를 요청한다. (10 -> 7)
    - schedule 메뉴에서 회의실에 대한 예약 정보를 알 수 있다.(Room Service + Reserve Service + Maintenance Service) (11)

### 헥사고날 아키텍쳐 다이어그램 도출 (Polyglot)
![무제](https://user-images.githubusercontent.com/33116855/109799525-7d3fc280-7c5f-11eb-8805-82b0752309e1.png)


# 구현
도출해낸 헥사고날 아키텍처에 맞게, 로컬에서 SpringBoot를 이용해 Maven 빌드 하였다. 각각의 포트넘버는 8081 ~ 8085, 8088 이다.

    cd conference
    mvn spring-boot:run
    
    cd gateway
    mvn spring-boot:run
    
    cd maintenance
    mvn spring-boot:run
    
    cd reserve
    mvn spring-boot:run
    
    cd room
    mvn spring-boot:run
    
    cd schedule
    mvn spring-boot:run
  
## DDD의 적용
**Maintenance 서비스의 Maintenance.java**

```java
package meetingroom;

import javax.persistence.*;
import org.springframework.beans.BeanUtils;
import java.util.List;

@Entity
@Table(name="Maintenance_table")
public class Maintenance {

    @Id
    @GeneratedValue(strategy=GenerationType.AUTO)
    private Long id;
    private Long roomId;
    private String status;

    @PrePersist
    public void onPrePersist(){
        //Following code causes dependency to external APIs
        // it is NOT A GOOD PRACTICE. instead, Event-Policy mapping is recommended.

        meetingroom.external.Reserve reserve = new meetingroom.external.Reserve();
        // mappings goes here
        reserve.setRoomId(roomId);
        String result = MaintenanceApplication.applicationContext.getBean(meetingroom.external.ReserveService.class).reserveCheck(reserve);

        if(result.equals("valid")){
            System.out.println("Success!");
            MaintStarted started = new MaintStarted();
            started.setRoomId(roomId);
            BeanUtils.copyProperties(this, started);
            started.publishAfterCommit();
        }
        else{
            /// reservecheck가 유효하지 않을 때 강제로 예외 발생
            System.out.println("FAIL!! Room is currently on reservation.");
            Exception ex = new Exception();
            ex.notify();
        }
    }

    @PreRemove
    public void onPreRemove(){
        MaintEnded ended = new MaintEnded();
        ended.setRoomId(roomId);
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
    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}

```

**Room 서비스의 PolicyHandler.java**
```java
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
    @StreamListener(KafkaProcessor.INPUT)
    public void wheneverMaintStarted_(@Payload MaintStarted started){

        if(started.isMe()){
            Optional<Room> room = roomRepository.findById(started.getRoomId());
            System.out.println("##### listener  : " + started.toJson());
            if(room.isPresent()){
                room.get().setStatus("Maintenance");//회의실 수리중.
                roomRepository.save(room.get());
            }
        }
    }
    @StreamListener(KafkaProcessor.INPUT)
    public void wheneverMaintEnded_(@Payload MaintEnded ended){

        if(ended.isMe()){
            Optional<Room> room = roomRepository.findById(ended.getRoomId());
            System.out.println("##### listener  : " + ended.toJson());
            if(room.isPresent()){
                room.get().setStatus("Available");//회의실 수리 종료.
                roomRepository.save(room.get());
            }
        }
    }
}

```


- 적용 후 REST API의 테스트를 통해 정상적으로 작동함을 알 수 있었다.
- 회의실 등록(Added) 후 결과
<img width="950" alt="스크린샷 2021-03-03 오후 3 52 44" src="https://user-images.githubusercontent.com/33116855/109765746-9ed98380-7c38-11eb-90a8-121f9a3b91ea.png">


- 회의 정비 시작(MaintenanceStarted) 후 결과
<img width="887" alt="스크린샷 2021-03-03 오후 3 53 23" src="https://user-images.githubusercontent.com/33116855/109765800-aef16300-7c38-11eb-9275-414f829a1b07.png">


## Gateway 적용
API Gateway를 통해 마이크로 서비스들의 진입점을 하나로 진행하였다.
```yml
server:
  port: 8088

---

spring:
  profiles: default
  cloud:
    gateway:
      routes:
        - id: conference
          uri: http://localhost:8081
          predicates:
            - Path=/conferences/** 
        - id: reserve
          uri: http://localhost:8082
          predicates:
            - Path=/reserves/** 
        - id: room
          uri: http://localhost:8083
          predicates:
            - Path=/rooms/** 
        - id: schedule
          uri: http://localhost:8084
          predicates:
            - Path= /roomTables/**
        - id: maintenance
          uri: http://localhost:8085
          predicates:
            - Path= /maintenances/**
      globalcors:
        corsConfigurations:
          '[/**]':
            allowedOrigins:
              - "*"
            allowedMethods:
              - "*"
            allowedHeaders:
              - "*"
            allowCredentials: true


---

spring:
  profiles: docker
  cloud:
    gateway:
      routes:
        - id: conference
          uri: http://conference:8080
          predicates:
            - Path=/conferences/** 
        - id: reserve
          uri: http://reserve:8080
          predicates:
            - Path=/reserves/** 
        - id: room
          uri: http://room:8080
          predicates:
            - Path=/rooms/** 
        - id: schedule
          uri: http://schedule:8080
          predicates:
            - Path= /roomTables/**
        - id: maintenance
          uri: http://maintenance:8080
          predicates:
            - Path=/maintenance/** 
      globalcors:
        corsConfigurations:
          '[/**]':
            allowedOrigins:
              - "*"
            allowedMethods:
              - "*"
            allowedHeaders:
              - "*"
            allowCredentials: true

server:
  port: 8080

```

## Polyglot Persistence
- Maintenance 서비스의 경우, 다른 서비스들이 h2 저장소를 이용한 것과는 다르게 hsql을 이용하였다. 
- 이 작업을 통해 서비스들이 각각 다른 데이터베이스를 사용하더라도 전체적인 기능엔 문제가 없음을, 즉 Polyglot Persistence를 충족하였다.

<img width="480" alt="스크린샷 2021-03-03 오후 3 56 01" src="https://user-images.githubusercontent.com/33116855/109766032-fc6dd000-7c38-11eb-9cea-51bc6ca96ee1.png">


## 동기식 호출(Req/Res 방식)과 Fallback 처리

- maintenance 서비스의 external/ReserveService.java 내에 예약한 사용자가 맞는지 확인하는 Service 대행 인터페이스(Proxy)를 FeignClient를 이용하여 구현하였다.

```java
@FeignClient(name="reserve", url="${RESERVE}")
public interface ReserveService {

    @RequestMapping(method= RequestMethod.GET, path="/reserves/checkReserve")
    public String reserveCheck(@RequestBody Reserve reserve);
}
```
- maintenance 서비스의 Maintenance.java 내에 사용자 확인 후 결과에 따라 회의 시작을 진행할지, 진행하지 않을지 결정.(@PrePersist)
```java
@PrePersist
    public void onPrePersist(){
        //Following code causes dependency to external APIs
        // it is NOT A GOOD PRACTICE. instead, Event-Policy mapping is recommended.

        meetingroom.external.Reserve reserve = new meetingroom.external.Reserve();
        // mappings goes here
        reserve.setRoomId(roomId);
        String result = MaintenanceApplication.applicationContext.getBean(meetingroom.external.ReserveService.class).reserveCheck(reserve);

        if(result.equals("valid")){
            System.out.println("Success!");
            MaintStarted started = new MaintStarted();
            started.setRoomId(roomId);
            BeanUtils.copyProperties(this, started);
            started.publishAfterCommit();
        }
        else{
            /// reservecheck가 유효하지 않을 때 강제로 예외 발생
            System.out.println("FAIL!! Room is currently on reservation.");
            Exception ex = new Exception();
            ex.notify();
        }
    }
```
- 동기식 호출에서는 호출 시간에 따른 커플링이 발생하여, Reserve 시스템에 장애가 나면 회의 시작을 할 수가 없다. (Reserve 시스템에서 예약된 회의실인지 확인하므로)
  - reserve 서비스를 중지. 
  - maintenance 서비스에서 정비 요청 시 에러 발생. 
    <img width="1292" alt="스크린샷 2021-03-03 오후 4 02 24" src="https://user-images.githubusercontent.com/33116855/109768113-d7c72780-7c3b-11eb-868f-05276b9f780c.png">

  - reserve 서비스 재기동 후 다시 정비 시작 요청. 
    <img width="1396" alt="스크린샷 2021-03-03 오후 4 16 28" src="https://user-images.githubusercontent.com/33116855/109768146-e281bc80-7c3b-11eb-8918-9d9fcd73268f.png">
  

## 비동기식 호출 (Pub/Sub 방식)

- maintenance 서비스 내 Maintenance.java에서 아래와 같이 서비스 Pub 구현

```java
@PrePersist
    public void onPrePersist(){
        //Following code causes dependency to external APIs
        // it is NOT A GOOD PRACTICE. instead, Event-Policy mapping is recommended.

        meetingroom.external.Reserve reserve = new meetingroom.external.Reserve();
        // mappings goes here
        reserve.setRoomId(roomId);
        String result = MaintenanceApplication.applicationContext.getBean(meetingroom.external.ReserveService.class).reserveCheck(reserve);

        if(result.equals("valid")){
            System.out.println("Success!");
            MaintStarted started = new MaintStarted();
            started.setRoomId(roomId);
            BeanUtils.copyProperties(this, started);
            started.publishAfterCommit();
        }
        else{
            /// reservecheck가 유효하지 않을 때 강제로 예외 발생
            System.out.println("FAIL!! Room is currently on reservation.");
            Exception ex = new Exception();
            ex.notify();
        }
    }
```

- room 서비스 내 PolicyHandler.java 에서 아래와 같이 Sub 구현

```java
@Service
public class PolicyHandler{
    @Autowired
    RoomRepository roomRepository;

    //...
    @StreamListener(KafkaProcessor.INPUT)
    public void wheneverMaintStarted_(@Payload MaintStarted started){

        if(started.isMe()){
            Optional<Room> room = roomRepository.findById(started.getRoomId());
            System.out.println("##### listener  : " + started.toJson());
            if(room.isPresent()){
                room.get().setStatus("Maintenance");//회의실 수리중.
                roomRepository.save(room.get());
            }
        }
    }
  }
```
- 비동기 호출은 다른 서비스 하나가 비정상이어도 해당 메세지를 다른 메시지 큐에서 보관하고 있기에, 서비스가 다시 정상으로 돌아오게 되면 그 메시지를 처리하게 된다.
  - maintenance 서비스와 room 서비스가 둘 다 정상 작동을 하고 있을 경우, 이상이 없이 잘 된다. 
    <img width="1124" alt="스크린샷 2021-03-03 오후 4 20 20" src="https://user-images.githubusercontent.com/33116855/109768585-6f2c7a80-7c3c-11eb-86c6-d66eca8f8a55.png">

  - room 서비스를 중지시킨다.
  - reserve 서비스를 이용해 예약을 하여도 문제가 없이 동작한다. 
    <img width="1231" alt="스크린샷 2021-03-03 오후 4 20 35" src="https://user-images.githubusercontent.com/33116855/109768593-72276b00-7c3c-11eb-9a80-11c4e384d29b.png">

## CQRS

viewer인 schedule 서비스를 별도로 구현하여 아래와 같이 view를 출력한다.
- MaintenanceStarted 수행 후 schedule (정비 진행)
  <img width="830" alt="스크린샷 2021-03-03 오후 5 10 39" src="https://user-images.githubusercontent.com/33116855/109774146-62f7eb80-7c43-11eb-9318-7b4b54b80a18.png">


- MaintenanceEnded 수행 후 schedule (정비 종료)
  <img width="770" alt="스크린샷 2021-03-03 오후 5 12 55" src="https://user-images.githubusercontent.com/33116855/109774461-b5390c80-7c43-11eb-8c56-7900b728f960.png">


# 운영
## CI/CD 설정
- git에서 소스 가져오기
```
https://github.com/morrisk0301/meetingroom
```
- Build 하기
```
cd /meetingroom
cd conference
mvn package

cd ..
cd gateway
mvn package

cd ..
cd reserve
mvn package

cd ..
cd room
mvn package

cd ..
cd maintenance
mvn package

cd ..
cd schedule
mvn package
```
- Dockerlizing, ACR(Azure Container Registry에 Docker Image Push하기
```
cd /meetingroom
cd rental
az acr build --registry meetingroomacr --image meetingroomacr.azurecr.io/conference:latest .

cd ..
cd gateway
az acr build --registry meetingroomacr --image meetingroomacr.azurecr.io/gateway:latest .

cd ..
cd reserve
az acr build --registry meetingroomacr --image meetingroomacr.azurecr.io/reserve:latest .

cd ..
cd room
az acr build --registry meetingroomacr --image meetingroomacr.azurecr.io/room:latest .

cd ..
cd maintenance
az acr build --registry meetingroomacr --image meetingroomacr.azurecr.io/room:latest .

cd ..
cd schedule
az acr build --registry meetingroomacr --image meetingroomacr.azurecr.io/schedule:latest .
```

- ACR에서 이미지 가져와서 Kubernetes에서 Deploy하기

```
kubectl create deploy gateway --image=meetingroomacr.azurecr.io/gateway:latest
kubectl create deploy conference --image=meetingroomacr.azurecr.io/conference:latest
kubectl create deploy reserve --image=meetingroomacr.azurecr.io/reserve:latest
kubectl create deploy room --image=meetingroomacr.azurecr.io/room:latest
kubectl create deploy maintenance --image=meetingroomacr.azurecr.io/maintenance:latest
kubectl create deploy schedule --image=meetingroomacr.azurecr.io/schedule:latest
kubectl get all
```

- Kubectl Deploy 결과 확인  

  <img width="708" alt="스크린샷 2021-03-03 오후 5 22 51" src="https://user-images.githubusercontent.com/33116855/109775628-17464180-7c45-11eb-8222-53f6a661d339.png">


- Kubernetes에서 서비스 생성하기 (Docker 생성이기에 Port는 8080이며, Gateway는 LoadBalancer로 생성)

```
kubectl expose deploy gateway --type="LoadBalancer" --port=8080
kubectl expose deploy conference --type="ClusterIP" --port=8080
kubectl expose deploy reserve --type="ClusterIP" --port=8080
kubectl expose deploy room --type="ClusterIP" --port=8080
kubectl expose deploy maintenance --type="ClusterIP" --port=8080
kubectl expose deploy schedule --type="ClusterIP" --port=8080
kubectl get all
```

- Kubectl Expose 결과 확인  

<img width="699" alt="스크린샷 2021-03-03 오후 5 23 36" src="https://user-images.githubusercontent.com/33116855/109775713-3218b600-7c45-11eb-9800-feb325096c6c.png">

  
## 무정지 재배포
- 먼저 무정지 재배포가 100% 되는 것인지 확인하기 위해서 Autoscaler 이나 CB 설정을 제거함
- siege 로 배포작업 직전에 워크로드를 모니터링 함
```
siege -c10 -t60S -r10 -v --content-type "application/json" 'http://20.194.27.60:8080/maintenances POST {"roomId":3}'
```
- Readiness가 설정되지 않은 yml 파일로 배포 진행  

  <img width="551" alt="스크린샷 2021-03-03 오후 5 29 42" src="https://user-images.githubusercontent.com/33116855/109776557-0cd87780-7c46-11eb-89c4-7bbfc59032da.png">

```
kubectl apply -f deployment.yml
```

- 아래 그림과 같이, Kubernetes가 준비가 되지 않은 maintenance pod에 요청을 보내서 siege의 Availability 가 100% 미만으로 떨어짐 

  <img width="396" alt="스크린샷 2021-03-03 오후 5 56 58" src="https://user-images.githubusercontent.com/33116855/109779973-dc92d800-7c49-11eb-849f-d6ba42037c4f.png">

- Readiness가 설정된 yml 파일로 배포 진행  

  <img width="610" alt="스크린샷 2021-03-03 오후 5 30 19" src="https://user-images.githubusercontent.com/33116855/109776619-22e63800-7c46-11eb-817b-e9498356660b.png">

```
kubectl apply -f deployment.yml
```
- 배포 중 pod가 2개가 뜨고, 새롭게 띄운 pod가 준비될 때까지, 기존 pod가 유지됨을 확인  

  <img width="678" alt="스크린샷 2021-03-03 오후 5 58 21" src="https://user-images.githubusercontent.com/33116855/109780138-0f3cd080-7c4a-11eb-9fd1-91511796ebde.png">
  
- siege 가 중단되지 않고, Availability가 높아졌음을 확인하여 무정지 재배포가 됨을 확인함  
  
  <img width="382" alt="스크린샷 2021-03-03 오후 6 00 58" src="https://user-images.githubusercontent.com/33116855/109780484-693d9600-7c4a-11eb-846f-06f4ba536f5a.png">


## 오토스케일 아웃
- 서킷 브레이커는 시스템을 안정되게 운영할 수 있게 해줬지만, 사용자의 요청이 급증하는 경우, 오토스케일 아웃이 필요하다.

  - 단, 부하가 제대로 걸리기 위해서, maintenance 서비스의 리소스를 줄여서 재배포한다.

  <img width="544" alt="스크린샷 2021-03-03 오후 7 48 36" src="https://user-images.githubusercontent.com/33116855/109794721-8037b480-7c59-11eb-9543-6f2fb2f48d24.png">

- maintenance 시스템에 replica를 자동으로 늘려줄 수 있도록 HPA를 설정한다. 설정은 CPU 사용량이 15%를 넘어서면 replica를 10개까지 늘려준다.

```
kubectl autoscale deploy maintenance --min=1 --max=10 --cpu-percent=15
```

- hpa 설정 확인  

  <img width="705" alt="스크린샷 2021-03-03 오후 7 59 34" src="https://user-images.githubusercontent.com/33116855/109796006-fbe63100-7c5a-11eb-83a6-5676e8ecbee4.png">

- hpa 상세 설정 확인  

  <img width="1232" alt="스크린샷 2021-03-03 오후 8 00 50" src="https://user-images.githubusercontent.com/33116855/109796153-289a4880-7c5b-11eb-9efc-8242f5b64872.png">

  
- siege를 활용해서 워크로드를 2분간 걸어준다. (Cloud 내 siege pod에서 부하줄 것)
```
siege -c100 -t120S -r10 -v --content-type "application/json" 'http://20.194.27.60:8080/maintenances POST {"roomId":3}'
```

- 오토스케일이 어떻게 되고 있는지 모니터링을 걸어둔다.
```
watch kubectl get all
```
- 스케일 아웃이 자동으로 되었음을 확인

  <img width="707" alt="스크린샷 2021-03-03 오후 8 03 29" src="https://user-images.githubusercontent.com/33116855/109796474-8b8bdf80-7c5b-11eb-980a-5e8cf49fa4cd.png">

- 오토스케일링에 따라 Siege 성공률이 높은 것을 확인 할 수 있다.  

  <img width="376" alt="스크린샷 2021-03-03 오후 8 07 30" src="https://user-images.githubusercontent.com/33116855/109796914-17057080-7c5c-11eb-8b3a-7aa202ee29e8.png">


## Self-healing (Liveness Probe)
- maintenance 서비스의 yml 파일에 liveness probe 설정을 바꾸어서, liveness probe 가 동작함을 확인

- liveness probe 옵션을 추가하되, 서비스 포트가 아닌 8090으로 설정, readiness probe 미적용
```
        livenessProbe:
            httpGet:
              path: '/actuator/health'
              port: 8090
            initialDelaySeconds: 5
            timeoutSeconds: 2
            periodSeconds: 5
            failureThreshold: 5
```

- maintenance 서비스에 liveness가 적용된 것을 확인  

  <img width="1285" alt="스크린샷 2021-03-03 오후 8 09 10" src="https://user-images.githubusercontent.com/33116855/109797086-52a03a80-7c5c-11eb-90e7-81227158bde4.png">
  
- maintenance 서비스에 liveness가 발동되었고, 8090 포트에 응답이 없기에 Restart가 발생함   

  <img width="769" alt="스크린샷 2021-03-03 오후 8 11 54" src="https://user-images.githubusercontent.com/33116855/109797361-b591d180-7c5c-11eb-9f3e-3ddb6efebeae.png">


## ConfigMap 적용

- maintenance 서비스의 deployment.yaml에 ConfigMap 적용 대상 항목을 추가한다.

  <img width="398" alt="스크린샷 2021-03-03 오후 5 46 52" src="https://user-images.githubusercontent.com/33116855/109778677-722d6800-7c48-11eb-8889-68328624d7bf.png">

- ConfigMap 생성하기
```
kubectl create configmap apiurl --from-literal=reserveapiurl=http://reserve:8080
```

- Configmap 생성 확인, url이 Configmap에 설정된 것처럼 잘 반영된 것을 확인할 수 있다.  

```
kubectl get configmap apiurl -o yaml
```
  <img width="732" alt="스크린샷 2021-03-03 오후 5 47 56" src="https://user-images.githubusercontent.com/33116855/109778813-98eb9e80-7c48-11eb-86d8-75ea3341fb17.png">


- 아래 코드와 같이 Spring Boot 내에서 Configmap 환경 변수를 사용하면 정상 작동한다.

   <img width="652" alt="스크린샷 2021-03-03 오후 5 48 22" src="https://user-images.githubusercontent.com/33116855/109778856-a739ba80-7c48-11eb-82dd-e7d7129b7a43.png">


## 동기식 호출 / 서킷 브레이킹 / 장애격리

* 서킷 브레이킹 프레임워크의 선택: Spring FeignClient + Hystrix 옵션을 사용하여 구현함

- RestAPI 기반 Request/Response 요청이 과도할 경우 CB 를 통하여 장애격리 하도록 설정함.

- Hystrix 를 설정:  요청처리 쓰레드에서 처리시간이 610 밀리가 넘어서기 시작하여 어느정도 유지되면 CB 회로가 닫히도록 설정

- maintenance Application.yaml 설정
```
feign:
  hystrix:
    enabled: true
    
hystrix:
  command:
    default:
      execution.isolation.thread.timeoutInMilliseconds: 610

```

- maintenance Thread 지연 코드 삽입

  <img width="620" alt="스크린샷 2021-03-03 오후 8 15 44" src="https://user-images.githubusercontent.com/33116855/109797774-3fda3580-7c5d-11eb-9ce3-56bf0764d1cf.png">


* 부하테스터 siege 툴을 통한 서킷 브레이커 동작 확인:
- 동시사용자 100명
- 60초 동안 실시

```
siege -c100 -t60S -r10 -v --content-type "application/json" 'http://20.194.27.60:8080/maintenances POST {"roomId":3}'
```
- 부하 발생하여 CB가 발동하여 요청 실패처리하였고, 밀린 부하가 reserve에서 처리되면서 다시 maintenance 받기 시작 

  <img width="763" alt="스크린샷 2021-03-03 오후 8 18 45" src="https://user-images.githubusercontent.com/33116855/109798103-a9f2da80-7c5d-11eb-8b96-b062e043fc10.png">
  <img width="420" alt="스크린샷 2021-03-03 오후 8 19 12" src="https://user-images.githubusercontent.com/33116855/109798136-b9722380-7c5d-11eb-8588-a25f2d96f00e.png">

- CB 잘 적용됨을 확인


