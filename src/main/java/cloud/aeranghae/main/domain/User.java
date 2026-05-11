package cloud.aeranghae.main.domain;

import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@Entity
@Table(name = "users") // PostgreSQL 등에서 user가 예약어인 경우가 많아 users로 씁니다.
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String email;

    @Column
    private String picture;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @Builder
    public User(String name, String email, String picture, Role role) {
        this.name = name;
        this.email = email;
        this.picture = picture;
        this.role = role;
    }

    public User update(String name, String picture) {
        // 이미 정식 유저(USER)로 승급해서 닉네임을 설정한 상태라면, 이름은 덮어쓰지 않음!
        if (this.role == Role.GUEST) {
            this.name = name;
        }
        // 프로필 사진은 구글에서 바꿨을 수 있으니 업데이트 해줌
        this.picture = picture;
        return this;
    }

    public String getRoleKey() {
        return this.role.getKey();
    }

    public void authorizeUser(String nickname) {
        this.name = nickname; // 닉네임 업데이트
        this.role = Role.USER; // 권한을 정식 유저로 승급
    }

    public void updateNickname(String newNickname) {
        // 공백 검증 등 추가적인 비즈니스 룰을 여기서 체크해도 좋을듯
        this.name = newNickname;
    }
}