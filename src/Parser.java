import java.util.ArrayList;
import java.util.List;

/**
 * BLOOP Parser
 *
 * Reads the token list produced by the Tokenizer and builds a
 * List<Instruction> that the Evaluator (Interpreter) can execute.
 *
 * BLOOP grammar (simplified):
 *
 *   program     → instruction* EOF
 *   instruction → assignStmt | printStmt | ifStmt | repeatStmt
 *
 *   assignStmt  → PUT expression INTO IDENTIFIER NEWLINE
 *   printStmt   → PRINT expression NEWLINE
 *   ifStmt      → IF expression THEN COLON NEWLINE body
 *   repeatStmt  → REPEAT NUMBER TIMES COLON NEWLINE body
 *   body        → (NEWLINE | instruction)+  (indented block until unindented line)
 *
 * Expression precedence (low → high):
 *   comparison  → + / -
 *   addSub      → * / /
 *   term        → primary
 *   primary     → NUMBER | STRING | IDENTIFIER | "(" expression ")"
 */
public class Parser {

    private final List<Token> tokens;
    private int current; // index of the token we are currently examining

    public Parser(List<Token> tokens) {
        this.tokens  = tokens;
        this.current = 0;
    }

    // ---------------------------------------------------------------
    // Public API
    // ---------------------------------------------------------------

    public List<Instruction> parse() {
        List<Instruction> instructions = new ArrayList<>();
        skipNewlines();

        while (!isAtEnd()) {
            Instruction inst = parseInstruction();
            if (inst != null) {
                instructions.add(inst);
            }
            skipNewlines();
        }
        return instructions;
    }

    // ---------------------------------------------------------------
    // Instruction parsers
    // ---------------------------------------------------------------

    private Instruction parseInstruction() {
        Token t = peek();

        if (t.getType() == TokenType.PUT) {
            return parseAssign();
        } else if (t.getType() == TokenType.PRINT) {
            return parsePrint();
        } else if (t.getType() == TokenType.IF) {
            return parseIf();
        } else if (t.getType() == TokenType.REPEAT) {
            return parseRepeat();
        } else if (t.getType() == TokenType.NEWLINE || t.getType() == TokenType.EOF) {
            advance(); // skip blank lines
            return null;
        } else {
            throw new RuntimeException("Unexpected token '" + t.getValue() +
                    "' at line " + t.getLine() +
                    ". Expected 'put', 'print', 'if', or 'repeat'.");
        }
    }

    /**
     * put <expression> into <identifier>
     */
    private Instruction parseAssign() {
        consume(TokenType.PUT, "Expected 'put'");
        Expression expr = parseExpression();
        consume(TokenType.INTO, "Expected 'into' after expression");
        Token nameToken = consume(TokenType.IDENTIFIER, "Expected variable name after 'into'");
        expectEndOfStatement();
        return new AssignInstruction(nameToken.getValue(), expr);
    }

    /**
     * print <expression>
     */
    private Instruction parsePrint() {
        consume(TokenType.PRINT, "Expected 'print'");
        Expression expr = parseExpression();
        expectEndOfStatement();
        return new PrintInstruction(expr);
    }

    /**
     * if <expression> then:
     *     <body>
     */
    private Instruction parseIf() {
        consume(TokenType.IF, "Expected 'if'");
        Expression condition = parseExpression();
        consume(TokenType.THEN, "Expected 'then' after condition");
        Token colonToken = consume(TokenType.COLON, "Expected ':' after 'then'");
        int headerLine = colonToken.getLine();
        expectNewline();

        List<Instruction> body = parseBlock(headerLine);
        return new IfInstruction(condition, body);
    }

    /**
     * repeat <number> times:
     *     <body>
     */
    private Instruction parseRepeat() {
        consume(TokenType.REPEAT, "Expected 'repeat'");
        Token countToken = consume(TokenType.NUMBER, "Expected a number after 'repeat'");
        int count = (int) Double.parseDouble(countToken.getValue());
        consume(TokenType.TIMES, "Expected 'times' after repeat count");
        Token colonToken = consume(TokenType.COLON, "Expected ':' after 'times'");
        int headerLine = colonToken.getLine();
        expectNewline();

        List<Instruction> body = parseBlock(headerLine);
        return new RepeatInstruction(count, body);
    }

    /**
     * A block is one or more indented instructions.
     * We detect the block by checking whether the current line
     * starts with whitespace (we already consumed all leading spaces
     * in the Tokenizer, so instead we track that the token is NOT
     * a top-level keyword at column 0).
     *
     * Simple approach: after a colon+newline, keep reading instructions
     * until we hit EOF or a line that starts with a top-level keyword
     * that is NOT indented.  Since our Tokenizer strips leading spaces,
     * we use indentation heuristic: collect instructions that appear
     * before a line whose first token is a top-level keyword at the same
     * level as the block opener.
     *
     * We implement this with a look-ahead: peek ahead, if the next
     * meaningful token is a top-level statement starter and we have NOT
     * seen any indentation signal, the block ends.
     *
     * For simplicity (and correctness for the sample programs), we treat
     * all lines between the opening colon+newline and the next un-indented
     * top-level keyword (or EOF) as the block body.
     */
    /**
     * Parse a block body.
     *
     * Strategy: we record the line of the block header (the line with
     * the colon).  Every line whose first token has a line number
     * STRICTLY GREATER than the header line is part of the body.
     * The first token we see whose line number is <= headerLine ends
     * the block.
     *
     * This relies on the Tokenizer tagging each token with the line it
     * came from, which it does.
     */
    private List<Instruction> parseBlock(int headerLine) {
        List<Instruction> body = new ArrayList<>();
        skipNewlines();

        while (!isAtEnd()) {
            // If the current token's line is at or before the header
            // line, the body is done.
            if (peek().getLine() <= headerLine) break;
            if (peek().getType() == TokenType.EOF) break;
            if (peek().getType() == TokenType.NEWLINE) { advance(); continue; }

            Instruction inst = parseInstruction();
            if (inst != null) {
                body.add(inst);
            }
            skipNewlines();
        }
        return body;
    }

    // ---------------------------------------------------------------
    // Expression parsers  (recursive descent, handles precedence)
    // ---------------------------------------------------------------

    /**
     * parseExpression — handles comparisons (>, <, ==)
     * These have the LOWEST precedence.
     */
    private Expression parseExpression() {
        Expression left = parseAddSub();

        while (check(TokenType.GREATER) || check(TokenType.LESS) || check(TokenType.EQUAL_EQUAL)) {
            Token opToken = advance();
            String op = opToken.getValue();
            Expression right = parseAddSub();
            left = new BinaryOpNode(left, op, right);
        }
        return left;
    }

    /**
     * parseAddSub — handles + and -
     */
    private Expression parseAddSub() {
        Expression left = parseTerm();

        while (check(TokenType.PLUS) || check(TokenType.MINUS)) {
            Token opToken = advance();
            String op = opToken.getValue();
            Expression right = parseTerm();
            left = new BinaryOpNode(left, op, right);
        }
        return left;
    }

    /**
     * parseTerm — handles * and /
     * Higher precedence than + and -.
     */
    private Expression parseTerm() {
        Expression left = parsePrimary();

        while (check(TokenType.STAR) || check(TokenType.SLASH)) {
            Token opToken = advance();
            String op = opToken.getValue();
            Expression right = parsePrimary();
            left = new BinaryOpNode(left, op, right);
        }
        return left;
    }

    /**
     * parsePrimary — a single number, string, or variable name.
     */
    private Expression parsePrimary() {
        Token t = peek();

        if (t.getType() == TokenType.NUMBER) {
            advance();
            return new NumberNode(Double.parseDouble(t.getValue()));
        }

        if (t.getType() == TokenType.STRING) {
            advance();
            return new StringNode(t.getValue());
        }

        if (t.getType() == TokenType.IDENTIFIER) {
            advance();
            return new VariableNode(t.getValue());
        }

        throw new RuntimeException("Expected a number, string, or variable name, " +
                "but got '" + t.getValue() + "' at line " + t.getLine());
    }

    // ---------------------------------------------------------------
    // Token navigation helpers
    // ---------------------------------------------------------------

    /** Return the current token without consuming it. */
    private Token peek() {
        return tokens.get(current);
    }

    /** Consume and return the current token. */
    private Token advance() {
        if (!isAtEnd()) current++;
        return tokens.get(current - 1);
    }

    /** True if the current token has the given type. */
    private boolean check(TokenType type) {
        return !isAtEnd() && peek().getType() == type;
    }

    /** Consume the current token if it matches; otherwise throw. */
    private Token consume(TokenType type, String message) {
        if (check(type)) return advance();
        Token t = peek();
        throw new RuntimeException(message + " (got '" + t.getValue() +
                "' at line " + t.getLine() + ")");
    }

    /** True if we have reached EOF. */
    private boolean isAtEnd() {
        return peek().getType() == TokenType.EOF;
    }

    /** Skip zero or more NEWLINE tokens. */
    private void skipNewlines() {
        while (!isAtEnd() && peek().getType() == TokenType.NEWLINE) {
            advance();
        }
    }

    /** After an instruction, expect a NEWLINE or EOF. */
    private void expectEndOfStatement() {
        if (!isAtEnd() && peek().getType() != TokenType.NEWLINE) {
            Token t = peek();
            throw new RuntimeException("Expected end of line after statement, " +
                    "got '" + t.getValue() + "' at line " + t.getLine());
        }
        if (!isAtEnd()) advance(); // consume the NEWLINE
    }

    /** Expect a newline (used after block headers like 'then:' and 'times:'). */
    private void expectNewline() {
        if (!isAtEnd() && peek().getType() == TokenType.NEWLINE) {
            advance();
        }
    }
}
