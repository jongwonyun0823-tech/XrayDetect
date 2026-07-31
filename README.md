# XrayDetect

행동 패턴 기반으로 X-ray 사용 의심 플레이어를 탐지하는 경량 Paper/Spigot 플러그인입니다.

> ⚠️ 서버 플러그인은 클라이언트 리소스팩(텍스처) 사용 여부를 직접 감지할 수 없습니다.
> 대신 "주변이 완전히 막혀 있던 광석을 바로 캐는" 행동 패턴을 감지하여 의심 점수를 매기는 방식입니다.

## 기능
- 다이아몬드, 고대 잔해, 에메랄드 등 고가치 광석 채굴 감시
- 노출면이 0개인 상태(벽 너머)에서 채굴 시 가중치 부여
- 임계값 초과 시 관리자에게 실시간 알림 + 파일 로그 기록 (`plugins/XrayDetect/xray_log.txt`)
- 오탐 방지를 위한 점수 자동 감소(decay) 기능
- 인접 6블록만 검사하므로 서버 성능에 거의 영향 없음

## 빌드
```bash
mvn clean package
```
`target/XrayDetect.jar` 파일을 서버의 `plugins` 폴더에 넣으면 됩니다.

## 권한
| 권한 | 설명 | 기본값 |
|---|---|---|
| `xraydetect.alert` | 의심 알림 수신 | op |
| `xraydetect.bypass` | 탐지 대상에서 제외 | op |

## 설정 (`config.yml`)
- `threshold`: 알림이 발생하는 의심 점수 기준
- `hidden-multiplier`: 완전히 막힌 광석 채굴 시 점수 배수
- `decay-interval-minutes`, `decay-amount`: 점수 자동 감소 주기/양
- `ore-weights`: 감시할 광석과 기본 점수

## 라이선스
자유롭게 수정/배포하세요.
