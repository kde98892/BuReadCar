# 부읽카 디자인 빌더 (Figma 로컬 플러그인)

Figma 파일 `VS6ksVAN3ICCiwvVmnSJIi`에 부읽카 디자인을 만드는 개발용 플러그인입니다. UI 없이 실행되고, 끝나면 결과 메시지를 띄웁니다.

## 먼저 있어야 하는 것

파일에 이미 만들어 둔 아래 항목을 그대로 씁니다.

- 변수 컬렉션: `Pleos · Color Light`, `Pleos · Color Dark`, `Pleos · Dimension`
- 텍스트 스타일 25개 (Asta Sans), 그림자 스타일 `Shadow/Level 1~3`
- 페이지 3개와 ② 컴포넌트 페이지의 `Icons · 48dp` 아이콘 29개

## 처음 한 번: 불러오기

1. Figma **데스크톱 앱**에서 파일을 엽니다. 개발용 플러그인은 브라우저에서 실행할 수 없습니다.
2. 왼쪽 위 Figma 메뉴 → **Plugins → Development → Import plugin from manifest…**
3. 이 폴더의 `manifest.json`을 고릅니다.

## 실행

**Plugins → Development → 부읽카 디자인 빌더** 에서 고릅니다.

| 메뉴 | 하는 일 |
| --- | --- |
| 전체 만들기 | ② 컴포넌트, ① 04·05 보드, ③ 화면을 모두 새로 만듭니다 |
| 문서 보드만 다시 만들기 | 04·05 보드만 다시 만듭니다. 컴포넌트는 기존 것을 씁니다 |
| 화면만 다시 만들기 | ③ 화면만 다시 만듭니다. 컴포넌트는 기존 것을 씁니다 |

- 1~2분 걸릴 수 있고, 그동안 Figma 화면이 멈춘 것처럼 보일 수 있습니다.
- 다시 실행하면 이 플러그인이 만든 노드만 지우고 새로 만듭니다. 직접 그린 노드는 지우지 않습니다.
- 결과와 경고는 ① 페이지 위쪽의 `빌드 로그 (지워도 됨)` 프레임에 남습니다.

## 만드는 것

- ② 컴포넌트: Button, Icon button, Text button, Toggle button, Tab item, Tabs, Switch, Radio, Checkbox, List row, Dropdown, Dropdown item, Dropdown menu, App icon, Page navigation, Toast, System popup, Indicator와 부읽카 전용 Segment progress, Region chip, Week day, Category tile, Schedule slot, Program card, Route stop. 색과 여백, 모서리, 높이는 변수에 연결됩니다.
- ① 04 보드: 타이포그래피, 그림자, 인터랙션·모션, 아이콘, 앱 아이콘, 디자인 토큰·테마, 3D 에셋
- ① 05 보드: Pleos 컴포넌트 22종의 구조·상태·속성·적용 수치와 부읽카 전용 컴포넌트
- ③ 화면: 가로 9개, 세로 4개, 다크 4개, 2560×1440 디스플레이 배치 2개
