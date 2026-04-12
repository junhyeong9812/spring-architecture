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

    // ─────────────────────────────────────────────
    // 1단계: 토큰 정의
    // ─────────────────────────────────────────────
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

        @Override
        public String toString() {
            return type + "(\"" + value + "\")";
        }
    }

    // ─────────────────────────────────────────────
    // 2단계: 렉서 (Lexer) — 문자열 → 토큰 리스트
    // ─────────────────────────────────────────────
    static class Lexer {
        private final String source;
        private int pos = 0;

        Lexer(String source) {
            this.source = source;
        }

        List<Token> tokenize() {
            List<Token> tokens = new ArrayList<>();

            while (pos < source.length()) {
                skipWhitespace();
                if (pos >= source.length()) break;

                char c = source.charAt(pos);

                if (c == '(') { tokens.add(new Token(TokenType.LPAREN, "(")); pos++; }
                else if (c == ')') { tokens.add(new Token(TokenType.RPAREN, ")")); pos++; }
                else if (c == '{') {
                    // { 를 만나면 내부 본문을 통째로 읽는다
                    tokens.add(new Token(TokenType.LBRACE, "{"));
                    pos++;
                    String body = readBraceBody();
                    if (!body.isBlank()) {
                        tokens.add(new Token(TokenType.BODY_CONTENT, body.trim()));
                    }
                    tokens.add(new Token(TokenType.RBRACE, "}"));
                }
                else if (c == ',') { tokens.add(new Token(TokenType.COMMA, ",")); pos++; }
                else if (c == ';') { tokens.add(new Token(TokenType.SEMICOLON, ";")); pos++; }
                else if (Character.isJavaIdentifierStart(c)) {
                    String word = readIdentifier();
                    switch (word) {
                        case "record"     -> tokens.add(new Token(TokenType.RECORD, word));
                        case "implements" -> tokens.add(new Token(TokenType.IMPLEMENTS, word));
                        default           -> tokens.add(new Token(TokenType.IDENTIFIER, word));
                    }
                }
                else {
                    pos++; // 알 수 없는 문자 스킵
                }
            }

            tokens.add(new Token(TokenType.EOF, ""));
            return tokens;
        }

        private void skipWhitespace() {
            while (pos < source.length() && Character.isWhitespace(source.charAt(pos))) pos++;
        }

        private String readIdentifier() {
            int start = pos;
            while (pos < source.length() && (Character.isJavaIdentifierPart(source.charAt(pos)) || source.charAt(pos) == '.')) {
                pos++;
            }
            // 제네릭 타입 처리: List<String> 같은 경우
            if (pos < source.length() && source.charAt(pos) == '<') {
                int depth = 0;
                do {
                    if (source.charAt(pos) == '<') depth++;
                    else if (source.charAt(pos) == '>') depth--;
                    pos++;
                } while (depth > 0 && pos < source.length());
            }
            return source.substring(start, pos);
        }

        private String readBraceBody() {
            int depth = 1;
            int start = pos;
            while (pos < source.length() && depth > 0) {
                if (source.charAt(pos) == '{') depth++;
                else if (source.charAt(pos) == '}') depth--;
                if (depth > 0) pos++;
            }
            String body = source.substring(start, pos);
            // pos는 } 를 가리키고 있으므로 한 칸 전진
            if (pos < source.length()) pos++;
            return body;
        }
    }

    // ─────────────────────────────────────────────
    // 3단계: AST 노드 정의
    // ─────────────────────────────────────────────
    static class RecordComponent {
        final String type;
        final String name;

        RecordComponent(String type, String name) {
            this.type = type;
            this.name = name;
        }

        boolean isPrimitive() {
            return switch (type) {
                case "int", "long", "short", "byte",
                     "float", "double", "char", "boolean" -> true;
                default -> false;
            };
        }
    }

    static class RecordDeclaration {
        String name;
        List<RecordComponent> components = new ArrayList<>();
        List<String> implementsList = new ArrayList<>();
        String compactConstructorBody = null; // 컴팩트 생성자 분문

        RecordDeclaraction(String name) {this.name = name; }
    }

    // ─────────────────────────────────────────────
    // 4단계: 파서 (Parser) — 토큰 → AST
    // ─────────────────────────────────────────────
    static class Parser {
        private final List<Token> tokens;
        private int pos = 0;

        Parser(List<Token> tokens) {
            this.tokens = tokens;
        }

        RecordDeclaration parse() {
            expect(TokenType.RECORD);                     // 'record'
            String name = expect(TokenType.IDENTIFIER).value;  // 클래스명
            RecordDeclaration decl = new RecordDeclaration(name);

            // 컴포넌트 파싱: (Type name, Type name, ...)
            expect(TokenType.LPAREN);
            if (peek().type != TokenType.RPAREN) {
                do {
                    String type = expect(TokenType.IDENTIFIER).value;
                    String fieldName = expect(TokenType.IDENTIFIER).value;
                    decl.components.add(new RecordComponent(type, fieldName));
                } while (matchAndConsume(TokenType.COMMA));
            }
            expect(TokenType.RPAREN);

            // implements 절 파싱 (선택)
            if (peek().type == TokenType.IMPLEMENTS) {
                advance();
                do {
                    decl.implementsList.add(expect(TokenType.IDENTIFIER).value);
                } while (matchAndConsume(TokenType.COMMA));
            }

            // 본문 파싱
            expect(TokenType.LBRACE);
            if (peek().type == TokenType.BODY_CONTENT) {
                decl.compactConstructorBody = advance().value;
            }
            expect(TokenType.RBRACE);

            return decl;
        }

        private Token peek() { return tokens.get(pos); }
        private Token advance() { return tokens.get(pos++); }

        private Token expect(TokenType type) {
            Token t = advance();
            if (t.type != type) {
                throw new RuntimeException(
                        "파싱 에러: " + type + " 을 기대했지만 " + t + " 을 만남 (위치: " + pos + ")");
            }
            return t;
        }

        private boolean matchAndConsume(TokenType type) {
            if (peek().type == type) { advance(); return true; }
            return false;
        }
    }

    static class CodeGenerator {
        private final RecordDeclaration decl;
        private final StringBuilder sb = new StringBuilder();
        private int indent = 0;

        CodeGenerator(RecordDeclaration decl) {this.decl = decl; }

        String generate() {
            generateImports();
            generateClassHeader();
            generateFields();
            generateConstructor();
            generateAccessors();
            generateToString();
            generateEquals();
            generateHashCode();
            line("}");
            return sb.toString();
        }

        private void generateImports() {
            line("import java.util.Objects;");
            line("");
        }

        // 클래스 선언
        private void generateClassHeader() {
            StringBuilder header = new StringBuilder();
            header.append("public final calss ").append(decl.name);
            header.append(" extends Record");

            if (!decl.implementsList.isEmpty()) {
                header.append(" implements ");
                header.append(String.join(", ", decl.implementsList));
            }
            header.append(" {");
            line(header.toString());
            line("");
            indent++:
        }

        // private final 필드 생성
        private void generateFields() {
            line("// ── 컴파일러 생성: private final 필드 ──");
            for (RecordComponent comp : decl.components) {
                line("private final " + comp.type + " " + comp.name + ";");
            }
            line("");
        }

        // 정규 생성자.
        private void generateConstructor() {

            line("")

            String params = decl.components.stream()
                    .map( c -> c.type + " " + c.name)
                    .collect(ollectors.joining(", "));

            line("public " + decl.name + "(" + params + ") {");
            indent++;

            if (decl.compactConstructorBody != null) {
                line("사용자 작성 컴팩트 생성자 본문");
                for (String bodyLine : decl.compactConstructorBody.split("\n")) {
                    if (!bodyLine.isBlank()) {
                        line(bodyLine.trim());
                    }
                }
                line("");
            }

            // 필드 할당은 항상 컴팩트 생성자 본문 "뒤"에 자동 삽입된다.
            line("컴파일러 자동 삽입")
            for (RecordComponent comp : decl.components) {
                line("this."+ comp.name + " = " + comp.name + ";");
            }

            indent--;
            line("}");
            line("");
        }

        private void generateAccessors() {
            line("컴파일러 생성;")
            for (RecordComponent comp: decl.components) {
                line("public " + comp.type + " " + comp.name + "() {");
                indent++;
                line("return this." + comp.name + ";");
                indent--;
                line("}");
                line("");
            }
        }

        private void generateToString() {
            line(" 컴파일러 생성 ")
            line("@Override")
            line("public String toString() {");
            indent++;

            StringBuilder format = new StringBuilder();
            format.append("return \"").append(decl.name).append("[\"");

            for (int i = 0; i < decl.components.size(); i++) {
                RecordComponent comp = decl.components.get(i);
                if ( i > 0 ) format.append(" + \", \"");
                format.append(" + \"").append(comp.name).append("=\" + this.").append(comp.name);
            }
            format.append(" + \"]\";");

            line(format.toString());
            indent--;
            line("}");
            line("");
        }

        private void generateEquals() {
            line("// ── 컴파일러 생성: equals ──");
            line("@Override");
            line("public boolean equals(Object o) {");
            indent++;
            line("if (this == o) return true;");
            line("if (!(o instanceof " + decl.name + " other)) return false;");

            StringBuilder comparison = new StringBuilder("return ");
            for (int i = 0; i < decl.components.size(); i++) {
                RecordComponent comp = decl.rmfjgrp components.get(i);
                if (i > 0) comparison.append("\n" + "    ".repeat(indent) + "    && ");

                if (comp.isPrimitive()) {
                    // 원시 타입은 == 비교
                    comparison.append("this.").append(comp.name)
                            .append(" == other.").append(comp.name);
                } else {
                    // 참조 타입은 Objects.equals (null-safe)
                    comparison.append("Objects.equals(this.").append(comp.name)
                            .append(", other.").append(comp.name).append(")");
                }
            }
            comparison.append(";");
            line(comparison.toString());

            indent--;
            line("}");
            line("");
        }

        private void generateHashCode() {
            line("// ── 컴파일러 생성: hashCode ──");
            line("@Override");
            line("public int hashCode() {");
            indent++;

            String fields = decl.components.stream()
                    .map(c -> "this." + c.name)
                    .collect(Collectors.joining(", "));
            line("return Objects.hash(" + fields + ");");

            indent--;
            line("}");
        }

        private void line(String text) {
            sb.append("    ".repeat(indent)).append(text).append("\n");
        }
    }

    public static void main(String[] args) {
        String[] testCases = {
                // 테스트 1: 기본 Record
                "record Point(int x, int y) {}",

                // 테스트 2: 참조 타입 포함
                "record Person(String name, int age, String email) {}",

                // 테스트 3: 컴팩트 생성자 + implements
                """
            record Range(int lo, int hi) implements Comparable<Range> {
                if (lo > hi) throw new IllegalArgumentException("lo must be <= hi");
            }
            """,

                // 테스트 4: 값 정규화가 있는 컴팩트 생성자
                """
            record Name(String first, String last) {
                first = first.trim().toLowerCase();
                last = last.trim().toLowerCase();
            }
            """
        };

        for (int i = 0; i < testCases.length; i++) {
            String source = testCases[i].trim();

            System.out.println("═".repeat(70));
            System.out.println("  입력 " + (i + 1) + ": " + source.lines().findFirst().orElse(""));
            System.out.println("═".repeat(70));

            // PHASE 1: 렉싱
            System.out.println("\n[1단계 - LEXER] 토큰 분리:");
            Lexer lexer = new Lexer(source);
            List<Token> tokens = lexer.tokenize();
            tokens.forEach(t -> System.out.println("  " + t));

            // PHASE 2: 파싱
            System.out.println("\n[2단계 - PARSER] AST 구성:");
            Parser parser = new Parser(tokens);
            RecordDeclaration decl = parser.parse();
            System.out.println("  클래스명: " + decl.name);
            System.out.println("  컴포넌트: " + decl.components.stream()
                    .map(c -> c.type + " " + c.name + (c.isPrimitive() ? " [원시]" : " [참조]"))
                    .collect(Collectors.joining(", ")));
            if (!decl.implementsList.isEmpty()) {
                System.out.println("  구현: " + String.join(", ", decl.implementsList));
            }
            if (decl.compactConstructorBody != null) {
                System.out.println("  컴팩트 생성자: 있음");
            }

            // PHASE 3: 코드 생성
            System.out.println("\n[3단계 - CODE GEN] 생성된 Java 코드:");
            System.out.println("─".repeat(70));
            CodeGenerator gen = new CodeGenerator(decl);
            System.out.println(gen.generate());
            System.out.println();
        }
    }
}