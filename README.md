# meetingroom


# 운영
## CI/CD 설정
- git에서 소스 가져오기
```
https://github.com/dngur6344/meetingroom
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
cd schedule
az acr build --registry meetingroomacr --image meetingroomacr.azurecr.io/schedule:latest .
```
- ACR에서 이미지 가져와서 Kubernetes에서 Deploy하기
```
kubectl create deploy gateway --image=meetingroomacr.azurecr.io/gateway:latest
kubectl create deploy conference --image=meetingroomacr.azurecr.io/conference:latest
kubectl create deploy reserve --image=meetingroomacr.azurecr.io/reserve:latest
kubectl create deploy room --image=meetingroomacr.azurecr.io/room:latest
kubectl create deploy schedule --image=meetingroomacr.azurecr.io/schedule:latest
kubectl get all
```
- Kubectl Deploy 결과 확인  
  <img width="556" alt="스크린샷 2021-02-28 오후 12 47 12" src="https://user-images.githubusercontent.com/33116855/109407331-52394280-79c3-11eb-8283-ba98b2899f69.png">

- Kubernetes에서 서비스 생성하기 (Docker 생성이기에 Port는 8080이며, Gateway는 LoadBalancer로 생성)
```
kubectl expose deploy conference --type="ClusterIP" --port=8080
kubectl expose deploy reserve --type="ClusterIP" --port=8080
kubectl expose deploy room --type="ClusterIP" --port=8080
kubectl expose deploy schedule --type="ClusterIP" --port=8080
kubectl expose deploy gateway --type="LoadBalancer" --port=8080
kubectl get all
```
- Kubectl Expose 결과 확인  
  <img width="646" alt="스크린샷 2021-02-28 오후 12 47 50" src="https://user-images.githubusercontent.com/33116855/109407339-5feec800-79c3-11eb-9f3f-18d9d2b812f0.png">


  
## 무정지 재배포
- 먼저 무정지 재배포가 100% 되는 것인지 확인하기 위해서 Autoscaler 이나 CB 설정을 제거함
- siege 로 배포작업 직전에 워크로드를 모니터링 함
```
siege -c10 -t60S -r10 -v --content-type "application/json" 'http://52.231.13.109:8080/reserves POST {"userId":1, "roomId":"3"}'
```
- Readiness가 설정되지 않은 yml 파일로 배포 진행  
  <img width="871" alt="스크린샷 2021-02-28 오후 1 52 52" src="https://user-images.githubusercontent.com/33116855/109408363-4b62fd80-79cc-11eb-9014-638a09b545c1.png">

```
kubectl apply -f deployment.yml
```
- 아래 그림과 같이, Kubernetes가 준비가 되지 않은 delivery pod에 요청을 보내서 siege의 Availability 가 100% 미만으로 떨어짐 
  <img width="480" alt="스크린샷 2021-02-28 오후 2 30 37" src="https://user-images.githubusercontent.com/33116855/109408933-97fd0780-79d1-11eb-8ec6-f17d44161eb5.png">

- Readiness가 설정된 yml 파일로 배포 진행  
  <img width="779" alt="스크린샷 2021-02-28 오후 2 32 51" src="https://user-images.githubusercontent.com/33116855/109408971-e4484780-79d1-11eb-8989-cd680e962eff.png">

```
kubectl apply -f deployment.yml
```
- 배포 중 pod가 2개가 뜨고, 새롭게 띄운 pod가 준비될 때까지, 기존 pod가 유지됨을 확인  
  <img width="764" alt="스크린샷 2021-02-28 오후 2 34 54" src="https://user-images.githubusercontent.com/33116855/109408992-2b363d00-79d2-11eb-8024-07aeade9e928.png">
  
- siege 가 중단되지 않고, Availability가 높아졌음을 확인하여 무정지 재배포가 됨을 확인함  
  <img width="507" alt="스크린샷 2021-02-28 오후 2 48 28" src="https://user-images.githubusercontent.com/33116855/109409209-093dba00-79d4-11eb-9793-d1a7cdbe55f0.png">


## 오토스케일 아웃
- 서킷 브레이커는 시스템을 안정되게 운영할 수 있게 해줬지만, 사용자의 요청이 급증하는 경우, 오토스케일 아웃이 필요하다.

  - 단, 부하가 제대로 걸리기 위해서, reserve 서비스의 리소스를 줄여서 재배포한다.
    <img width="703" alt="스크린샷 2021-02-28 오후 2 51 19" src="https://user-images.githubusercontent.com/33116855/109409248-7d785d80-79d4-11eb-95ce-4af79b9a7e72.png">

- recipe 시스템에 replica를 자동으로 늘려줄 수 있도록 HPA를 설정한다. 설정은 CPU 사용량이 15%를 넘어서면 replica를 10개까지 늘려준다.
```
kubectl autoscale deploy reserve --min=1 --max=10 --cpu-percent=15
```

- hpa 설정 확인  
  <img width="631" alt="스크린샷 2021-02-28 오후 2 56 50" src="https://user-images.githubusercontent.com/33116855/109409360-6a19c200-79d5-11eb-90a4-fc5c5030e92b.png">

- hpa 상세 설정 확인  
  <img width="1327" alt="스크린샷 2021-02-28 오후 2 57 37" src="https://user-images.githubusercontent.com/33116855/109409362-6ede7600-79d5-11eb-85ec-85c59bdefcaf.png">
  <img width="691" alt="스크린샷 2021-02-28 오후 2 57 53" src="https://user-images.githubusercontent.com/33116855/109409364-700fa300-79d5-11eb-8077-70d5cddf7505.png">

  
- siege를 활용해서 워크로드를 2분간 걸어준다. (Cloud 내 siege pod에서 부하줄 것)
```
kubectl exec -it (siege POD 이름) -- /bin/bash
siege -c1000 -t120S -r100 -v --content-type "application/json" 'http://20.194.45.67:8080/reserves POST {"userId":1, "roomId":"3"}'
```

- 오토스케일이 어떻게 되고 있는지 모니터링을 걸어둔다.
```
watch kubectl get all
```
- 스케일 아웃이 자동으로 되었음을 확인
  <img width="656" alt="스크린샷 2021-02-28 오후 3 01 47" src="https://user-images.githubusercontent.com/33116855/109409423-eb715480-79d5-11eb-8b2c-0a0417df9718.png">

- 오토스케일링에 따라 Siege 성공률이 높은 것을 확인 할 수 있다.  
  <img width="412" alt="스크린샷 2021-02-28 오후 3 03 18" src="https://user-images.githubusercontent.com/33116855/109409445-18be0280-79d6-11eb-9c6f-4632f8a88d1d.png">

## Self-healing (Liveness Probe)
- reserve 서비스의 yml 파일에 liveness probe 설정을 바꾸어서, liveness probe 가 동작함을 확인

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
  <img width="824" alt="스크린샷 2021-02-28 오후 3 31 53" src="https://user-images.githubusercontent.com/33116855/109409951-1bbaf200-79da-11eb-9a39-a585224c3ca0.png">

- book 에 liveness가 발동되었고, 8090 포트에 응답이 없기에 Restart가 발생함   
  <img width="643" alt="스크린샷 2021-02-28 오후 3 34 35" src="https://user-images.githubusercontent.com/33116855/109409994-7c4a2f00-79da-11eb-8ab7-e542e50fd929.png">

## ConfigMap 적용
- reserve의 application.yaml에 ConfigMap 적용 대상 항목을 추가한다.
  <img width="558" alt="스크린샷 2021-02-28 오후 4 01 52" src="https://user-images.githubusercontent.com/33116855/109410475-4f981680-79de-11eb-8231-0679b6f5f55b.png">

- reserve의 service.yaml에 ConfigMap 적용 대상 항목을 추가한다.
  <img width="325" alt="스크린샷 2021-02-28 오후 4 05 07" src="https://user-images.githubusercontent.com/33116855/109410532-c03f3300-79de-11eb-8e61-71752818c41d.png">

- ConfigMap 생성하기
```
kubectl create configmap apiurl --from-literal=conferenceapiurl=http://conference:8080 --from-literal=roomapiurl=http://room:8080
```

- Configmap 생성 확인  
```
kubectl get configmap apiurl -o yaml
```
  <img width="447" alt="스크린샷 2021-02-28 오후 4 08 16" src="https://user-images.githubusercontent.com/33116855/109410590-33e14000-79df-11eb-93ed-bdfb04778cd8.png">
   <img width="625" alt="스크린샷 2021-02-28 오후 4 10 11" src="https://user-images.githubusercontent.com/33116855/109410630-8884bb00-79df-11eb-99d4-f6311cbe37bd.png">


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
