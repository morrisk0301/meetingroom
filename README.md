# meetingroom


# 운영
## CI/CD 설정
- git에서 소스 가져오기
```
git clone https://github.com/HorangApple/rentalbook
```
- Build 하기
```
cd /rentalbook
cd rental
mvn package

cd ..
cd book
mvn package

cd ..
cd system
mvn package

cd ..
cd gateway
mvn package

cd ..
cd mypage
mvn package
```
- Dockerlizing, ACR(Azure Container Registry에 Docker Image Push하기
```
cd /rentalbook
cd rental
az acr build --registry intensive2021 --image intensive2021.azurecr.io/rental:v2 .

cd ..
cd book
az acr build --registry intensive2021 --image intensive2021.azurecr.io/book:v1 .

cd ..
cd system
az acr build --registry intensive2021 --image intensive2021.azurecr.io/system:v1 .

cd ..
cd gateway
az acr build --registry intensive2021 --image intensive2021.azurecr.io/gateway:v1 .

cd ..
cd mypage
az acr build --registry intensive2021 --image intensive2021.azurecr.io/mypage:v1 .
```
- ACR에서 이미지 가져와서 Kubernetes에서 Deploy하기
```
kubectl create deploy rental --image=intensive2021.azurecr.io/rental:v2
kubectl create deploy book --image=intensive2021.azurecr.io/book:v1
kubectl create deploy system --image=intensive2021.azurecr.io/system:v1
kubectl create deploy gateway --image=intensive2021.azurecr.io/gateway:v1
kubectl create deploy mypage --image=intensive2021.azurecr.io/mypage:v1
kubectl get all
```
- Kubectl Deploy 결과 확인  
![2021-02-03 134410](https://user-images.githubusercontent.com/12531980/106704114-db609200-662e-11eb-88fa-fb91db5801d5.png)
- Kubernetes에서 서비스 생성하기 (Docker 생성이기에 Port는 8080이며, Gateway는 LoadBalancer로 생성)
```
kubectl expose deploy rental --type="ClusterIP" --port=8080
kubectl expose deploy book --type="ClusterIP" --port=8080
kubectl expose deploy system --type="ClusterIP" --port=8080
kubectl expose deploy gateway --type="LoadBalancer" --port=8080
kubectl expose deploy mypage --type="ClusterIP" --port=8080
kubectl get all
```
- Kubectl Expose 결과 확인  
  ![2021-02-03 134513](https://user-images.githubusercontent.com/12531980/106704135-e4516380-662e-11eb-9cd9-b0f416cea7ac.png)
  
- 테스트를 위해서 Kafka zookeeper와 server도 별도로 실행 필요 ([참고](http://msaschool.io/operation/implementation/implementation-seven/))

  ```
  kubectl -n kafka exec -ti my-kafka-0 -- /usr/bin/kafka-console-consumer --bootstrap-server my-kafka:9092 --topic rentalbook --from-beginning
  ```

  
## 무정지 재배포
- 먼저 무정지 재배포가 100% 되는 것인지 확인하기 위해서 Autoscaler 이나 CB 설정을 제거함
- siege 로 배포작업 직전에 워크로드를 모니터링 함
```
siege -c100 -t60S -r10 -v http get http://book:8080/books
```
- Readiness가 설정되지 않은 yml 파일로 배포 진행  
  ![2021-02-03 151653](https://user-images.githubusercontent.com/12531980/106706409-fd5c1380-6632-11eb-8169-c33d867030a3.png)
```
kubectl apply -f deployment_without_readiness.yml
```
- 아래 그림과 같이, Kubernetes가 준비가 되지 않은 delivery pod에 요청을 보내서 siege의 Availability 가 100% 미만으로 떨어짐
- 중간에 socket에 끊겨서 siege 명령어 종료됨 (서비스 정지 발생)  
  ![2021-02-03 151722](https://user-images.githubusercontent.com/12531980/106706432-051bb800-6633-11eb-9e77-88b1f4c3a3df.png)
- 무정지 재배포 여부 확인 전에, siege 로 배포작업 직전에 워크로드를 모니터링
```
siege -c100 -t60S -r10 -v http get http://book:8080/books
```
- Readiness가 설정된 yml 파일로 배포 진행  
  ![2021-02-03 153237](https://user-images.githubusercontent.com/12531980/106707743-149c0080-6635-11eb-8cb4-a55f9a7c368c.png)
```
kubectl apply -f deployment_with_readiness.yml
```
- 배포 중 pod가 2개가 뜨고, 새롭게 띄운 pod가 준비될 때까지, 기존 pod가 유지됨을 확인  
  ![2021-02-03 152954](https://user-images.githubusercontent.com/12531980/106707816-339a9280-6635-11eb-8763-e9292917e135.png)
  ![2021-02-03 153014](https://user-images.githubusercontent.com/12531980/106707844-401eeb00-6635-11eb-8772-abc2b5599fd7.png)
  
- siege 가 중단되지 않고, Availability가 높아졌음을 확인하여 무정지 재배포가 됨을 확인함  
  ![2021-02-03 153117](https://user-images.githubusercontent.com/12531980/106707873-48772600-6635-11eb-8240-4a248a928956.png)

## 오토스케일 아웃
- 서킷 브레이커는 시스템을 안정되게 운영할 수 있게 해줬지만, 사용자의 요청이 급증하는 경우, 오토스케일 아웃이 필요하다.

  - 단, 부하가 제대로 걸리기 위해서, recipe 서비스의 리소스를 줄여서 재배포한다.
```
kubectl apply -f - <<EOF
  apiVersion: apps/v1
  kind: Deployment
  metadata:
    name: system
    namespace: default
    labels:
      app: system
  spec:
    replicas: 1
    selector:
      matchLabels:
        app: system
    template:
      metadata:
        labels:
          app: system
      spec:
        containers:
          - name: system
            image: intensive2021.azurecr.io/system:v1
            ports:
              - containerPort: 8080
            resources:
              limits:
                cpu: 500m
              requests:
                cpu: 200m
EOF
```

- 다시 expose 해준다.
```
kubectl expose deploy system --type="ClusterIP" --port=8080
```

- recipe 시스템에 replica를 자동으로 늘려줄 수 있도록 HPA를 설정한다. 설정은 CPU 사용량이 15%를 넘어서면 replica를 10개까지 늘려준다.
```
kubectl autoscale deploy system --min=1 --max=10 --cpu-percent=15
```

- hpa 설정 확인  
  ![2021-02-03 154629](https://user-images.githubusercontent.com/12531980/106713188-6e082d80-663d-11eb-9ad9-6acd77945286.png)

- hpa 상세 설정 확인  
  ![2021-02-03 154702](https://user-images.githubusercontent.com/12531980/106713445-d48d4b80-663d-11eb-82a8-3eb3f93eaad9.png)
  
- siege를 활용해서 워크로드를 2분간 걸어준다. (Cloud 내 siege pod에서 부하줄 것)
```
kubectl exec -it (siege POD 이름) -- /bin/bash
siege -c1000 -t120S -r100 -v --content-type "application/json" 'http://system:8080/reserves POST {"bookNm": "apple", "userNm": "melon", "bookId":1}'
```

- 오토스케일이 어떻게 되고 있는지 모니터링을 걸어둔다.
```
watch kubectl get all
```
- siege 실행 결과 표시  
  - 오토스케일이 되지 않아, siege 성공률이 낮다.

![2021-02-03 162654](https://user-images.githubusercontent.com/12531980/106713540-f090ed00-663d-11eb-8f6e-730669326576.png)

- 스케일 아웃이 자동으로 되었음을 확인
  ![2021-02-03 160548](https://user-images.githubusercontent.com/12531980/106713610-07374400-663e-11eb-8a9e-9bdb7b99487a.png)

- siege 재실행
```
kubectl exec -it (siege POD 이름) -- /bin/bash
siege -c1000 -t120S -r100 -v --content-type "application/json" 'http://system:8080/reserves POST {"bookNm": "apple", "userNm": "melon", "bookId":1}'
```

- siege 의 로그를 보아도 전체적인 성공률이 높아진 것을 확인 할 수 있다.  
  ![2021-02-03 162907](https://user-images.githubusercontent.com/12531980/106713642-13230600-663e-11eb-9ee6-b770d4852079.png)

## Self-healing (Liveness Probe)
- book 서비스의 yml 파일에 liveness probe 설정을 바꾸어서, liveness probe 가 동작함을 확인

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

- book 서비스에 liveness가 적용된 것을 확인  
  ![2021-02-03 164738](https://user-images.githubusercontent.com/12531980/106714929-dce68600-663f-11eb-955a-6be70c5b0685.png)

- book 에 liveness가 발동되었고, 8090 포트에 응답이 없기에 Restart가 발생함   
  ![2021-02-03 164929](https://user-images.githubusercontent.com/12531980/106714955-e4a62a80-663f-11eb-8710-163015fd7b54.png)

## ConfigMap 적용
- ConfigMap을 활용하여 변수를 서비스에 이식한다.
- ConfigMap 생성하기
```
kubectl create configmap systemword --from-literal=word=Booking
```

- Configmap 생성 확인  
  ![2021-02-03 180849](https://user-images.githubusercontent.com/12531980/106727078-7321a880-664e-11eb-85da-9d5515f55ad5.png)

- 소스 수정에 따른 Docker 이미지 변경이 필요하기에, 기존 system 서비스 삭제
```
kubectl delete pod,deploy,service system
```

- system 서비스의 Reserve.java (system\src\main\java\rentalbook) 수정
```
#22번째 줄을 아래와 같이 수정
#기존에는 Booking 라는 고정된 값이 출력되었으나, Configmap 에서 가져온 환경변수를 입력받도록 수정
// subscribed.setStatus("Booking");
subscribed.setStatus("Process in " + System.getenv("STATUS"));
```

- system 서비스의 Deployment.yml 파일에 아래 항목 추가하여 deployment_configmap.yml 생성 (아래 코드와 그림은 동일 내용)
```
          env:
            - name: STATUS
              valueFrom:
                configMapKeyRef:
                  name: systemword
                  key: word

```
  ![2021-02-03 181204](https://user-images.githubusercontent.com/12531980/106727115-7d43a700-664e-11eb-9c7a-102db5fde979.png)  

- Docker Image 다시 빌드하고, Repository 에 배포하기

- Kubernetes 에서 POD 생성할 때, 설정한 deployment_config.yml 파일로 생성하기
```
kubectl create -f deployment_config.yml
```

- Kubernetes에서 POD 생성 후 expose

- 해당 POD에 접속하여 Configmap 항목이 ENV에 있는지 확인  
  ![2021-02-03 182604](https://user-images.githubusercontent.com/12531980/106727246-9c423900-664e-11eb-8417-1802192e555a.png)

- http로 전송 후, Status에 Configmap의 Key값이 찍히는지 확인  
```
http post http://system:8080/reserves bookNm=apple userNm=melon bookId=1
```
  ![2021-02-03 183232](https://user-images.githubusercontent.com/12531980/106727284-a6643780-664e-11eb-8b2a-871b96ae9419.png)

- 참고: 기존에 configmap 사용 전에는 아래와 같이 status에 고정값이 출력됨  
  ![2021-02-03 170935](https://user-images.githubusercontent.com/12531980/106727344-b8de7100-664e-11eb-9e08-1d9c9fcbc5c2.png)



## 동기식 호출 / 서킷 브레이킹 / 장애격리
- istio를 활용하여 Circuit Breaker 동작을 확인한다.
- istio 설치를 먼저 한다. [참고-Lab. Istio Install](http://msaschool.io/operation/operation/operation-two/)
- istio injection이 enabled 된 namespace를 생성한다.
```
kubectl create namespace istio-test-ns
kubectl label namespace istio-test-ns istio-injection=enabled
```

- namespace label에 istio-injection이 enabled 된 것을 확인한다.  
  ![2021-02-03 190448](https://user-images.githubusercontent.com/12531980/106732891-93ecfc80-6654-11eb-82e2-694de9c85ad8.png)
  
- 해당 namespace에 기존 서비스들을 재배포한다.
  - 이 명령어로 생성된 pod에 들어가려면 -c 로 컨테이너를 지정해줘야 함
```
# kubectl로 deploy 실행 (실행 위치는 상관없음)
# 이미지 이름과 버전명에 유의

kubectl create deploy rental --image=intensive2021.azurecr.io/rental:v2 -n istio-test-ns
kubectl create deploy book --image=intensive2021.azurecr.io/book:v1 -n istio-test-ns
kubectl create deploy system --image=intensive2021.azurecr.io/system:v1 -n istio-test-ns
kubectl create deploy gateway --image=intensive2021.azurecr.io/gateway:v1 -n istio-test-ns
kubectl create deploy mypage --image=intensive2021.azurecr.io/mypage:v1 -n istio-test-ns
kubectl get all

#expose 하기
# (주의) expose할 때, gateway만 LoadBalancer고, 나머지는 ClusterIP임
kubectl expose deploy rental --type="ClusterIP" --port=8080 -n istio-test-ns
kubectl expose deploy book --type="ClusterIP" --port=8080 -n istio-test-ns
kubectl expose deploy system --type="ClusterIP" --port=8080 -n istio-test-ns
kubectl expose deploy gateway --type="LoadBalancer" --port=8080 -n istio-test-ns
kubectl expose deploy mypage --type="ClusterIP" --port=8080 -n istio-test-ns
kubectl get all
```

- 서비스들이 정상적으로 배포되었고, Container가 2개씩 생성된 것을 확인한다. (1개는 서비스 container, 다른 1개는 Sidecar 형태로 생성된 envoy)  
  ![2021-02-03 190615](https://user-images.githubusercontent.com/12531980/106732913-9c453780-6654-11eb-9577-396669c84188.png)

- gateway의 External IP를 확인하고, 서비스가 정상 작동함을 확인한다.
```
http post http://52.141.63.150:8080/reserves bookNm=apple userNm=melon bookId=1
```
  ![2021-02-03 190732](https://user-images.githubusercontent.com/12531980/106732938-a404dc00-6654-11eb-96d7-99954aa7e44a.png)

- Circuit Breaker 설정을 위해 아래와 같은 Destination Rule을 생성한다.

- Pending Request가 많을수록 오랫동안 쌓인 요청은 Response Time이 증가하게 되므로, 적절한 대기 쓰레드 풀을 적용하기 위해 connection pool을 설정했다.
```
kubectl apply -f - <<EOF
  apiVersion: networking.istio.io/v1alpha3
  kind: DestinationRule
  metadata:
    name: dr-httpbin
    namespace: istio-test-ns
  spec:
    host: gateway
    trafficPolicy:
      connectionPool:
        http:
          http1MaxPendingRequests: 1
          maxRequestsPerConnection: 1
EOF
```

- 설정된 Destinationrule을 확인한다.  
  ![2021-02-03 190935](https://user-images.githubusercontent.com/12531980/106732968-abc48080-6654-11eb-9684-83d152963379.png)

- siege 를 활용하여 User가 1명인 상황에 대해서 요청을 보낸다. (설정값 c1)
  - siege 는 같은 namespace 에 생성하고, 해당 pod 안에 들어가서 siege 요청을 실행한다.
```
kubectl exec -it (siege POD 이름) -c siege -n istio-test-ns -- /bin/bash

siege -c1 -t30S -v --content-type "application/json" 'http://52.141.63.150:8080/reserves POST {"bookNm": "apple", "userNm": "melon", "bookId":1}'
```

- 실행결과를 확인하니, Availability가 높게 나옴을 알 수 있다.  
  ![2021-02-03 191635](https://user-images.githubusercontent.com/12531980/106733004-b717ac00-6654-11eb-8b0e-a088c49a8b71.png)

- 이번에는 User 가 2명인 상황에 대해서 요청을 보내고, 결과를 확인한다.  
```
siege -c2 -t30S -v --content-type "application/json" 'http://52.141.63.150:8080/reserves POST {"bookNm": "apple", "userNm": "melon", "bookId":1}'
```

- Availability 가 User 가 1명일 때 보다 낮게 나옴을 알 수 있다. Circuit Breaker 가 동작하여 대기중인 요청을 끊은 것을 알 수 있다.  
  ![2021-02-03 191744](https://user-images.githubusercontent.com/12531980/106733056-c1d24100-6654-11eb-9fea-4df300c98af7.png)

## 모니터링, 앨럿팅
- 모니터링: istio가 설치될 때, Add-on으로 설치된 Kiali, Jaeger, Grafana로 데이터, 서비스에 대한 모니터링이 가능하다.

  - Kiali (istio-External-IP:20001)
  어플리케이션의 proxy 통신, 서비스매쉬를 한눈에 쉽게 확인할 수 있는 모니터링 툴  
   ![2021-02-03 192128](https://user-images.githubusercontent.com/12531980/106733307-14136200-6655-11eb-987c-9d279c9dd6c5.png)
   ![2021-02-03 192114](https://user-images.githubusercontent.com/12531980/106733285-0d84ea80-6655-11eb-9a56-6b57e13a8cf0.png)
  
  - Jaeger (istio-External-IP:80)
    트랜잭션을 추적하는 오픈소스로, 이벤트 전체를 파악하는 Tracing 툴  
  ![2021-02-03 192242](https://user-images.githubusercontent.com/12531980/106733427-36a57b00-6655-11eb-8cd4-12ae58a297dc.png)
  
  - Grafana (istio-External-IP:3000)
  시계열 데이터에 대한 대시보드이며, Prometheus를 통해 수집된 istio 관련 데이터를 보여줌
  ```
  kubectl edit svc grafana -n istio-system
  
  - 수정 커맨드에서 아래 명령어를 입력
  :%s/ClusterIP/LoadBalancer/g
  
  - 저장하고 닫기
  :wq!
  ```
  ![image](https://user-images.githubusercontent.com/16534043/106687835-451d7380-6610-11eb-9d54-257c3eb4b866.png)
