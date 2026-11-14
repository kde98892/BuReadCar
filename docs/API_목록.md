# 부.읽.카 API 목록

작성일: 2026-11-09

인증키는 이 문서에 적지 않는다. 프로젝트 루트의 `local.properties`에 아래 이름으로 넣는다. 이 파일은 git에 올리지 않는다.

| 키 이름 | 발급처 | 쓰는 API |
| --- | --- | --- |
| `DATA_GO_KR_API_KEY` | [공공데이터포털](https://www.data.go.kr/iim/api/selectAcountList.do) | 실거래가, 전월세, 청약 |
| `RONE_API_KEY` | [R-ONE 인증키 발급](https://www.reb.or.kr/r-one/portal/openapi/openApiActKeyPage.do) | 주택가격지수 |
| `ECOS_API_KEY` | [한국은행 ECOS](https://ecos.bok.or.kr/jsp/openapi/OpenApiController.jsp?t=main) | 금리 |
| `VWORLD_API_KEY` | [V-World 오픈API](https://www.vworld.kr/v4po_openapi_s001.do) | 좌표 → 시군구 변환 |

공공데이터포털 키는 URL 인코딩된 값을 그대로 사용한다.

## 1. 앱에서 사용 중인 API

상태 기준은 2026-11-09에 실제 호출에 성공했는지이다.

### 위치

| 용도 | 요청 URL | 주요 파라미터 | 상태 |
| --- | --- | --- | --- |
| 좌표 → 시군구, 법정동코드 | `https://api.vworld.kr/req/address` | `service=address`, `request=getAddress`, `version=2.0`, `crs=epsg:4326`, `point=경도,위도`, `format=json`, `type=PARCEL`, `key` | 호출 성공 |

- 문서: [V-World 좌표를 주소로 변환](https://www.vworld.kr/dev/v4dv_geocoderguide2_s002.do)
- 일일 요청은 무제한이지만 결과를 저장장치나 DB에 저장할 수 없다.
- `level4LC` 앞 5자리를 실거래가 API의 `LAWD_CD`로 쓴다.

### 실거래가·전월세 (공공데이터포털)

| 카테고리 | 요청 URL | 파라미터 | 상태 |
| --- | --- | --- | --- |
| 실거래가·신고가 | `https://apis.data.go.kr/1613000/RTMSDataSvcAptTradeDev/getRTMSDataSvcAptTradeDev` | `serviceKey`, `LAWD_CD`, `DEAL_YMD`(YYYYMM), `pageNo`, `numOfRows` | 호출 성공 |
| 전월세 시세 | `https://apis.data.go.kr/1613000/RTMSDataSvcAptRent/getRTMSDataSvcAptRent` | 위와 동일 | 호출 성공 |

### 분양·청약 (공공데이터포털)

| 용도 | 요청 URL | 파라미터 | 상태 |
| --- | --- | --- | --- |
| 분양 공고 | `https://api.odcloud.kr/api/ApplyhomeInfoDetailSvc/v1/getAPTLttotPblancDetail` | `page`, `perPage`, `cond[SUBSCRPT_AREA_CODE_NM::EQ]`, `serviceKey` | 호출 성공 |
| 청약 경쟁률 | `https://api.odcloud.kr/api/ApplyhomeInfoCmpetRtSvc/v1/getAPTLttotPblancCmpet` | `page`, `perPage`, `cond[HOUSE_MANAGE_NO::EQ]`, `serviceKey` | 호출 성공 |

- 데이터 안내: [청약 접수 경쟁률 조회 서비스](https://www.data.go.kr/data/15098905/openapi.do)

### 주택가격지수 (R-ONE)

| 용도 | 요청 URL | 파라미터 | 상태 |
| --- | --- | --- | --- |
| 지역 분류 목록 | `https://www.reb.or.kr/r-one/openapi/SttsApiTblItm.do` | `KEY`, `Type=json`, `pIndex`, `pSize`, `STATBL_ID=T244183132827305` | 호출 성공 |
| 주간 매매가격지수 | `https://www.reb.or.kr/r-one/openapi/SttsApiTblData.do` | `KEY`, `Type=json`, `pIndex`, `pSize`, `STATBL_ID=T244183132827305`, `DTACYCLE_CD=WK`, `CLS_ID`, `ITM_ID=10001`, `START_WRTTIME`, `END_WRTTIME` | 호출 성공 |

- 문서: [개발가이드](https://www.reb.or.kr/r-one/portal/openapi/openApiDevPage.do), [통계코드 검색](https://www.reb.or.kr/r-one/portal/openapi/openApiGuideCdPage.do)

### 금리·대출 (한국은행 ECOS)

요청 형식: `https://ecos.bok.or.kr/api/StatisticSearch/{KEY}/json/kr/{시작}/{끝}/{통계코드}/M/{YYYYMM}/{YYYYMM}/{항목코드}`

| 항목 | 통계코드 | 항목코드 | 상태 |
| --- | --- | --- | --- |
| 한국은행 기준금리 | `722Y001` | `0101000` | 호출 성공 |
| 주택담보대출 신규 금리 | `121Y006` | `BECBLA0302` | 호출 성공 |

### 교통·개발·재건축·정책·미분양 (국토교통부 보도자료)

인증키가 필요 없다. 첫 요청에 쿠키와 같은 주소로의 307 응답이 오므로, 쿠키를 붙여 다시 요청한다(`httpGetWithCookies`).

| 용도 | 요청 URL | 파라미터 | 상태 |
| --- | --- | --- | --- |
| 보도자료 목록 (제목, 분야, 등록일) | `https://www.molit.go.kr/USR/NEWS/m_71/lst.jsp` | `lcmspage` (1쪽에 10건) | 호출 성공 |
| 최신 보도자료 10건 (목록을 못 읽을 때) | `https://www.molit.go.kr/dev/board/board_rss.jsp?rss_id=NEWS` | 없음 | 호출 성공 |

- 최근 31일 글을 최대 10쪽까지 읽고 30분 동안 다시 쓴다.
- 본문: 방송에서 읽는 글만 상세 페이지(`dtl.jsp?id=`)에서 첨부한 보도자료 hwpx를 받아 `Contents/section0.xml`의 "□" 문단 첫 문장을 최대 3개 읽는다. 받지 못하면 제목만 읽는다. 한 문서는 20MB까지만 받는다.
- 저작권: 국토교통부 보도자료 저작물은 공공누리 공공저작물 자유이용허락에 따라 누구나 이용할 수 있다.
- 인증서: `*.molit.go.kr`은 OISTE WISeKey Global Root GB CA 체인이다. Android 14 시스템 인증서 저장소(`dfc0fe80.0`)에 있다.

## 2. 승인됐지만 앱에서 아직 쓰지 않는 API

| 서비스 (공공데이터포털) | 활용 후보 | 상태 |
| --- | --- | --- |
| 국토교통부 아파트 매매 실거래가 자료 (`https://apis.data.go.kr/1613000/RTMSDataSvcAptTrade/getRTMSDataSvcAptTrade`) | 상세 자료로 대체되어 미사용 | 호출 성공 |
| 국토교통부 아파트 분양권전매 실거래가 자료 (`https://apis.data.go.kr/1613000/RTMSDataSvcSilvTrade/getRTMSDataSvcSilvTrade`) | 분양·청약 추가 소재 | 호출은 정상, 시험한 분당구 2026년 9월은 0건 |
| 한국부동산원 전국지가변동률조사 마이크로데이터 조회 서비스 | 개발 호재의 수치 소재 | 미호출, 요청 URL 미확인 |

## 3. 더 찾아볼 소스

| 카테고리 | 후보 소스 | 쓰임 |
| --- | --- | --- |
| 재건축·재개발 | [서울 열린데이터광장](https://data.seoul.go.kr/dataList/38/literacyView.do) | 서울 정비사업 단계 같은 수치 |
| 미분양·입주물량 | 지자체 API, KOSIS, 통계누리, [R-ONE](https://www.reb.or.kr/r-one/portal/main/indexPage.do) | 시군구 단위 미분양 수치 |

참고 전용: [KB부동산 데이터허브](https://data.kbland.kr/). 공개 API와 앱 내 재사용 조건을 찾지 못해 앱에서 호출하지 않는다.

## 4. 차량 SDK와 플랫폼 API

| API | 의존성 또는 진입점 | 권한 | 문서 |
| --- | --- | --- | --- |
| Pleos NaviHelper | `ai.pleos.playground:NaviHelper:3.0.5` | `pleos.car.permission.NAVI_ROUTE` | [NaviHelper](https://document.pleos.ai/en/api-reference/connect-sdk-pleos/NaviHelper/intro) |
| Pleos TextToSpeech | `ai.pleos.playground:TextToSpeech:3.3.1`, `Mode.ON_DEVICE` | `pleos.car.permission.TTS_SERVICE` | [TextToSpeech](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/TextToSpeech/intro) |
| AAOS CarUxRestrictions | `android.car`, `CarUxRestrictionsManager` | 없음 | [주행 규제 개발 가이드](https://document.pleos.ai/docs/pleos-only/vehicle-app-developer-guide/driving-restriction-development-guide) |
| Android 오디오 포커스 | `AudioManager`, `AudioFocusRequest` | 없음 | [오디오 포커스 개발 가이드](https://document.pleos.ai/docs/pleos-only/vehicle-app-developer-guide/audio-focus-development-guide/audio-focus-development) |

Maven 저장소: `https://nexus-playground.pleos.ai/repository/maven-releases/`
