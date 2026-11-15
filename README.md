# 부.읽.카

부동산 읽어주는 카. 출퇴근 경로의 부동산 소식을 음성으로 읽어 주는 Pleos Connect 차량용 앱입니다. 월~금 요일마다 듣고 싶은 소식 카테고리를 최대 2개 골라 둘 수 있습니다.

지금은 설계 단계입니다. 자세한 내용은 [설계 문서](docs/부읽카_설계.md)를 보세요.

## API 키

API 키는 프로젝트 루트의 `api.properties`에 넣습니다. 심사용 키 공유를 위해 이 파일은 저장소에 포함합니다.

PC별 Android SDK 경로(`sdk.dir`)는 `local.properties`에 설정합니다. 이 파일은 Git에서 제외하며, Android Studio에서 각 PC의 SDK 경로를 설정해 주세요.

```properties
RONE_API_KEY=
VWORLD_API_KEY=
ECOS_API_KEY=
DATA_GO_KR_API_KEY=
```
