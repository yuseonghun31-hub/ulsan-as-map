# 울산 AS 지도 (PWA)

울산 AS 방문 일정·동선 지도를 폰 홈 화면에서 주소창·안내 배너 없이 여는 웹앱입니다.
정적 파일(GitHub Pages)은 화면만 담고 있고, 자료는 구글 시트에서 PIN 잠금 API로 받아옵니다.

- 주소: https://yuseonghun31-hub.github.io/ulsan-as-map/
- 첫 실행 때 PIN을 입력하면 그 기기(브라우저)에만 저장되어 다음부터는 바로 열립니다.
- 헤더 오른쪽 `잠금` 을 누르면 저장된 PIN이 지워지고 다시 PIN을 묻습니다.

## 파일

| 파일 | 역할 |
| --- | --- |
| `index.html` | 화면 — 지도 SVG·구/동 필터·경로 추천·드래그 정렬·네이버 지도 열기·접수번호 복사, PIN 잠금 |
| `manifest.json` | 앱 이름·아이콘·standalone(전체 화면) 설정 |
| `sw.js` | 화면·아이콘만 캐시(자료는 캐시하지 않음) |
| `icon-*.png` | 앱 아이콘 |

이 저장소에는 고객 정보가 없습니다. 지도 경계 좌표와 화면 코드만 있습니다.

## 자료 연결

`index.html` 의 `API` 상수가 가리키는 Apps Script 웹앱에 `?action=data&pin=…` 로 요청합니다.
PIN이 맞지 않으면 `{ok:false, unauthorized:true}` 가 오고 화면은 PIN 입력으로 돌아갑니다.
경로 추천 설정·순서 저장은 같은 주소로 POST(`{"action":"saveRoute","pin":…,"state":…}`)합니다.
