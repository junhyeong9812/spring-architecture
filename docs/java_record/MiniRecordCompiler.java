import java.util.*;
import java.util.regex.*;
import java.util.stream.*;

/**
 * Main Record Compiler
 * 실제 javac가 record 키워드를 만나면 하는 일을
 * 순수 Java로 재현한 미니 컴파일러
 *
 * 실행: javac MiniRecordCompiler.java && java MiniRecordCompiler
 *
 * [동작 흐름]
 * 1. PARSE - record 선언문을 토큰으로 분리하고 AST(추상 구문 트리)를 만든다.
 * 2. ANALYZE - 컴포넌트(필드) 정보를 추출한다.
 * 3. GENERATE - 완전한 java 클래스 소스코드를 생성한다.
 *      - final class 선언
 *      - private final 필드
 *      - 정규 생성자 (컴팩트 생성자 본문 포함)
 *      - 접근자 메서드
 *      - toString()
 *      - equals()
 *      - hashCode()
 */
public class MiniRecordCompiler {
    
    // 1단계: 토큰 정의
    enum TokenType {
        RECORD,         // 'record' 키워드
        IMPLEMENTS,     // 'implements' 키워드
        IDENTIFIER,     // 이름 (클래스명, 타입명, 필드명)
        LPAREN,         // (
        RPAREN,         // )
        LBRACE,         // {
        RBRACE,         // }
        COMMA,          // ,
        SEMICOLON,      // ;
        BODY_CONTENT,   // { ... } 안의 사용자 코드
        EOF
    }

    static class Token {
        final TokenType type;
        final String value;

        Token(TokenType type, String value) {
            this.type = type;
            this.value = value;
        }
    }

}