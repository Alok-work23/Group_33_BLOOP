import java.util.ArrayList;
import java.util.List;

/**
 * BLOOP Tokenizer
 * Reads raw source code and produces a flat list of Token objects.
 *
 * BLOOP keywords: put, into, print, if, then, repeat, times
 * Example line:   put x + y * 2 into result
 */
public class Tokenizer {

    private final String source;
    private int pos;        // current character index
    private int line;       // current line number (1-based)

    public Tokenizer(String source) {
        this.source = source;
        this.pos = 0;
        this.line = 1;
    }

    // ---------------------------------------------------------------
    // Public API
    // ---------------------------------------------------------------

    public List<Token> tokenize() {
        List<Token> tokens = new ArrayList<>();

        while (pos < source.length()) {
            skipSpacesAndTabs();

            if (pos >= source.length()) break;

            char c = source.charAt(pos);

            // --- Newline ---
            if (c == '\n') {
                tokens.add(new Token(TokenType.NEWLINE, "\\n", line));
                line++;
                pos++;
                continue;
            }

            // --- Windows-style line ending (\r\n) ---
            if (c == '\r') {
                pos++;
                continue;
            }

            // --- Single-line comment: # ... ---
            if (c == '#') {
                while (pos < source.length() && source.charAt(pos) != '\n') {
                    pos++;
                }
                continue;
            }

            // --- String literal: "..." ---
            if (c == '"') {
                tokens.add(readString());
                continue;
            }

            // --- Number literal ---
            if (Character.isDigit(c) || (c == '.' && pos + 1 < source.length() && Character.isDigit(source.charAt(pos + 1)))) {
                tokens.add(readNumber());
                continue;
            }

            // --- Identifier or keyword ---
            if (Character.isLetter(c) || c == '_') {
                tokens.add(readIdentifierOrKeyword());
                continue;
            }

            // --- Operators and punctuation ---
            switch (c) {
                case '+': tokens.add(new Token(TokenType.PLUS,    "+", line)); pos++; break;
                case '-': tokens.add(new Token(TokenType.MINUS,   "-", line)); pos++; break;
                case '*': tokens.add(new Token(TokenType.STAR,    "*", line)); pos++; break;
                case '/': tokens.add(new Token(TokenType.SLASH,   "/", line)); pos++; break;
                case '>': tokens.add(new Token(TokenType.GREATER, ">", line)); pos++; break;
                case '<': tokens.add(new Token(TokenType.LESS,    "<", line)); pos++; break;
                case ':': tokens.add(new Token(TokenType.COLON,   ":", line)); pos++; break;
                case '=':
                    if (pos + 1 < source.length() && source.charAt(pos + 1) == '=') {
                        tokens.add(new Token(TokenType.EQUAL_EQUAL, "==", line));
                        pos += 2;
                    } else {
                        throw new RuntimeException("Unexpected character '=' at line " + line +
                                ". Did you mean '=='?");
                    }
                    break;
                default:
                    throw new RuntimeException("Unknown character '" + c + "' at line " + line);
            }
        }

        tokens.add(new Token(TokenType.EOF, "", line));
        return tokens;
    }

    // ---------------------------------------------------------------
    // Private helpers
    // ---------------------------------------------------------------

    /** Skip spaces and horizontal tabs (but NOT newlines). */
    private void skipSpacesAndTabs() {
        while (pos < source.length()) {
            char c = source.charAt(pos);
            if (c == ' ' || c == '\t') {
                pos++;
            } else {
                break;
            }
        }
    }

    /** Read a quoted string token. Supports \" escape inside strings. */
    private Token readString() {
        int startLine = line;
        pos++; // skip opening "
        StringBuilder sb = new StringBuilder();

        while (pos < source.length()) {
            char c = source.charAt(pos);
            if (c == '"') {
                pos++; // skip closing "
                return new Token(TokenType.STRING, sb.toString(), startLine);
            }
            if (c == '\\' && pos + 1 < source.length() && source.charAt(pos + 1) == '"') {
                sb.append('"');
                pos += 2;
            } else {
                sb.append(c);
                pos++;
            }
        }
        throw new RuntimeException("Unterminated string starting at line " + startLine);
    }

    /** Read an integer or decimal number token. */
    private Token readNumber() {
        int startLine = line;
        StringBuilder sb = new StringBuilder();
        boolean hasDot = false;

        while (pos < source.length()) {
            char c = source.charAt(pos);
            if (Character.isDigit(c)) {
                sb.append(c);
                pos++;
            } else if (c == '.' && !hasDot) {
                hasDot = true;
                sb.append(c);
                pos++;
            } else {
                break;
            }
        }
        return new Token(TokenType.NUMBER, sb.toString(), startLine);
    }

    /** Read a word and decide if it is a keyword or an identifier. */
    private Token readIdentifierOrKeyword() {
        int startLine = line;
        StringBuilder sb = new StringBuilder();

        while (pos < source.length()) {
            char c = source.charAt(pos);
            if (Character.isLetterOrDigit(c) || c == '_') {
                sb.append(c);
                pos++;
            } else {
                break;
            }
        }

        String word = sb.toString();
        TokenType type = keyword(word);
        return new Token(type, word, startLine);
    }

    /** Map a word to its keyword TokenType, or IDENTIFIER if not a keyword. */
    private TokenType keyword(String word) {
        switch (word.toLowerCase()) {
            case "put":    return TokenType.PUT;
            case "into":   return TokenType.INTO;
            case "print":  return TokenType.PRINT;
            case "if":     return TokenType.IF;
            case "then":   return TokenType.THEN;
            case "repeat": return TokenType.REPEAT;
            case "times":  return TokenType.TIMES;
            default:       return TokenType.IDENTIFIER;
        }
    }
}

