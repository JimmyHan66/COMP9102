/*
 * Recogniser.java
 *
 * Wed 26 Feb 2025 14:06:17 AEDT
 */

/* This recogniser accepts a subset of VC defined by the following CFG:

	program       -> func-decl

	// declaration

	func-decl     -> void identifier "(" ")" compound-stmt

	identifier    -> ID

	// statements
	compound-stmt -> "{" stmt* "}"
	stmt          -> continue-stmt
	    	      |  expr-stmt
	continue-stmt -> continue ";"
	expr-stmt     -> expr? ";"

	// expressions
	expr                -> assignment-expr
	assignment-expr     -> additive-expr
	additive-expr       -> multiplicative-expr
	                    |  additive-expr "+" multiplicative-expr
	multiplicative-expr -> unary-expr
		            |  multiplicative-expr "*" unary-expr
	unary-expr          -> "-" unary-expr
			    |  primary-expr

	primary-expr        -> identifier
	 		    |  INTLITERAL
			    | "(" expr ")"

It serves as a good starting point for implementing your own VC recogniser.
You can modify the existing parsing methods (if necessary) and add any missing ones
to build a complete recogniser for VC.

Alternatively, you are free to disregard the starter code entirely and develop
your own solution, as long as it adheres to the same public interface.

*/

package VC.Recogniser;

import VC.Scanner.Scanner;
import VC.Scanner.SourcePosition;
import VC.Scanner.Token;
import VC.ErrorReporter;

public class Recogniser {

    private Scanner scanner;
    private ErrorReporter errorReporter;
    private Token currentToken;

    public Recogniser(Scanner lexer, ErrorReporter reporter) {
        scanner = lexer;
        errorReporter = reporter;
        currentToken = scanner.getToken();
    }

    // match checks to see if the current token matches tokenExpected.
    // If so, fetches the next token.
    // If not, reports a syntactic error.
    void match(int tokenExpected) throws SyntaxError {
        if (currentToken.kind == tokenExpected) {
            currentToken = scanner.getToken();
        } else {
            syntacticError("\"%\" expected here", Token.spell(tokenExpected));
        }
    }

    // accepts the current token and fetches the next
    void accept() {
        currentToken = scanner.getToken();
    }

    // Handles syntactic errors and reports them via the error reporter.
    void syntacticError(String messageTemplate, String tokenQuoted) throws SyntaxError {
        SourcePosition pos = currentToken.position;
        errorReporter.reportError(messageTemplate, tokenQuoted, pos);
        throw new SyntaxError();
    }

    // ========================== PROGRAMS ========================
    public void parseProgram() {
        try {
            while(currentToken.kind == Token.VOID || currentToken.kind == Token.INT || currentToken.kind == Token.FLOAT || currentToken.kind == Token.BOOLEAN) {
                parseDecl();
            }
            if (currentToken.kind != Token.EOF) {
                syntacticError("\"%\" wrong result type for a function", currentToken.spelling);
            }
        } catch (SyntaxError s) { }
    }

    // ========================== DECLARATIONS ========================
    void parseDecl() throws SyntaxError {
        parseType();
        parseIdent();
        // parse DeclRest
        if (currentToken.kind == Token.LPAREN) {
            parseFuncDecl();
        } else {
            parseVarDecl();
        }
    }

    void parseFuncDecl() throws SyntaxError {
        parseParaList();
        parseCompoundStmt();
    }

    void parseVarDecl() throws SyntaxError {
        if (currentToken.kind == Token.LBRACKET) {
            match(Token.LBRACKET);
            if (currentToken.kind == Token.INTLITERAL) {
                parseIntLiteral();
            }
            match(Token.RBRACKET);
        }

        if (currentToken.kind == Token.EQ) {
            match(Token.EQ);
            parseInitialiser();
        }
        while(currentToken.kind == Token.COMMA) {
            match(Token.COMMA);
            parseInitDeclarator();
        }
        match(Token.SEMICOLON);
    }

    void parseInitDeclarator() throws SyntaxError {
        parseIdent();
        if (currentToken.kind == Token.LBRACKET) {
            match(Token.LBRACKET);
            if (currentToken.kind == Token.INTLITERAL) {
                parseIntLiteral();
            }
            match(Token.RBRACKET);
        }

        if (currentToken.kind == Token.EQ) {
            match(Token.EQ);
            parseInitialiser();
        }
    }

    void parseInitialiser() throws SyntaxError {
        if(currentToken.kind == Token.LCURLY) {
            match(Token.LCURLY);
            parseExpr();

            while(currentToken.kind == Token.COMMA) {
                match(Token.COMMA);
                parseExpr();
            }
            match(Token.RCURLY);
        } else {
            parseExpr();
        }
    }

    // ========================== TYPES ========================
    void parseType() throws SyntaxError {
        switch (currentToken.kind) {
            case Token.VOID:
                match(Token.VOID);
                break;
            case Token.BOOLEAN:
                match(Token.BOOLEAN);
                break;
            case Token.INT:
                match(Token.INT);
                break;
            case Token.FLOAT:
                match(Token.FLOAT);
                break;
            default:
                syntacticError("\"%\" expected here", currentToken.spelling);
                break;
        }
    }
    // ======================= STATEMENTS ==============================
    void parseCompoundStmt() throws SyntaxError {
        match(Token.LCURLY);
        while(currentToken.kind == Token.VOID || currentToken.kind == Token.INT || currentToken.kind == Token.FLOAT || currentToken.kind == Token.BOOLEAN) {
                //parseDecl();
                parseType();
                parseIdent();
                if (currentToken.kind == Token.LPAREN) {
                    syntacticError("\"%\" expected here", Token.spell(Token.SEMICOLON));
                }
                parseVarDecl();
        }
        parseStmtList();
        match(Token.RCURLY);
    }

    // Defines a list of statements enclosed within curly braces
    void parseStmtList() throws SyntaxError {
        while (currentToken.kind != Token.RCURLY)
            parseStmt();
    }

    void parseStmt() throws SyntaxError {
        switch (currentToken.kind) {
            case Token.LCURLY:
                parseCompoundStmt();
                break;
            case Token.IF:
                parseIfStmt();
                break;
            case Token.FOR:
                parseForStmt();
                break;
            case Token.WHILE:
                parseWhileStmt();
                break;
            case Token.BREAK:
                parseBreakStmt();
                break;
            case Token.RETURN:
                parseReturnStmt();
                break;
            case Token.CONTINUE:
                parseContinueStmt();
                break;
            default:
                parseExprStmt();
                break;
        }
    }

    // Handles if statements
    void parseIfStmt() throws SyntaxError {
        match(Token.IF);
        match(Token.LPAREN);
        parseExpr();
        match(Token.RPAREN);
        parseStmt();
        if (currentToken.kind == Token.ELSE) {
            match(Token.ELSE);
            parseStmt();
        }
    }

    // Handles for statements
    void parseForStmt() throws SyntaxError {
        match(Token.FOR);
        match(Token.LPAREN);
        if(currentToken.kind != Token.SEMICOLON) {
            parseExpr();
        }
        match(Token.SEMICOLON);
        if(currentToken.kind!=Token.SEMICOLON) {
            parseExpr();
        }
        match(Token.SEMICOLON);
        if(currentToken.kind != Token.RPAREN) {
            parseExpr();
        }
        match(Token.RPAREN);
        parseStmt();
    }
    // Handles while statements
    void parseWhileStmt() throws SyntaxError {
        match(Token.WHILE);
        match(Token.LPAREN);
        parseExpr();
        match(Token.RPAREN);
        parseStmt();
    }

    // Handles break statements
    void parseBreakStmt() throws SyntaxError {
        match(Token.BREAK);
        match(Token.SEMICOLON);
    }
    // Handles return statements
    void parseReturnStmt() throws SyntaxError {
        match(Token.RETURN);
        if (currentToken.kind != Token.SEMICOLON) {
            parseExpr();
        }
        match(Token.SEMICOLON);
    }
    // Handles continue statements
    void parseContinueStmt() throws SyntaxError {
        match(Token.CONTINUE);
        match(Token.SEMICOLON);
    }

    // Handles expression statements, optionally parsing an expression followed by a semicolon
    void parseExprStmt() throws SyntaxError {
        if (currentToken.kind != Token.SEMICOLON) {
            parseExpr();
            match(Token.SEMICOLON);
        } else {
            match(Token.SEMICOLON);
        }
    }

    // ======================= IDENTIFIERS ======================
    // Calls parseIdent rather than match(Token.ID). In future assignments,
    // an Identifier node will be constructed in this method.
    void parseIdent() throws SyntaxError {
        if (currentToken.kind == Token.ID) {
            accept();
        } else {
            syntacticError("identifier expected here", "");
        }
    }

    // ======================= OPERATORS ======================
    // Calls acceptOperator rather than accept(). In future assignments,
    // an Operator Node will be constructed in this method.
    void acceptOperator() throws SyntaxError {
        currentToken = scanner.getToken();
    }

    // ======================= EXPRESSIONS ======================
    void parseExpr() throws SyntaxError {
        parseAssignExpr();
    }

    void parseAssignExpr() throws SyntaxError {
        parseCondOrExpr();
        if (currentToken.kind == Token.EQ) {
            acceptOperator();
            parseAssignExpr();
        }
    }

    void parseCondOrExpr() throws SyntaxError {
        parseCondAndExpr();
        while (currentToken.kind == Token.OROR) {
            acceptOperator();
            parseCondAndExpr();
        }
    }

    void parseCondAndExpr() throws SyntaxError {
        parseEqualityExpr();
        while(currentToken.kind == Token.ANDAND) {
            acceptOperator();
            parseEqualityExpr();
        }
    }

    void parseEqualityExpr() throws SyntaxError {
        parseRelExpr();
        while(currentToken.kind == Token.NOTEQ || currentToken.kind == Token.EQEQ) {
            acceptOperator();
            parseRelExpr();
        }
    }

    void parseRelExpr() throws SyntaxError {
        parseAdditiveExpr();
        while(currentToken.kind == Token.LT || currentToken.kind == Token.LTEQ || currentToken.kind == Token.GT || currentToken.kind == Token.GTEQ) {
            acceptOperator();
            parseAdditiveExpr();
        }
    }

    void parseAdditiveExpr() throws SyntaxError {
        parseMultiplicativeExpr();
        while (currentToken.kind == Token.PLUS||currentToken.kind == Token.MINUS) {
            acceptOperator();
            parseMultiplicativeExpr();
        }
    }

    void parseMultiplicativeExpr() throws SyntaxError {
        parseUnaryExpr();
        while (currentToken.kind == Token.MULT || currentToken.kind == Token.DIV) {
            acceptOperator();
            parseUnaryExpr();
        }
    }

    void parseUnaryExpr() throws SyntaxError {
        switch (currentToken.kind) {
            case Token.PLUS:
            case Token.MINUS:
            case Token.NOT:
                acceptOperator();
                parseUnaryExpr();
                break;
            default:
                parsePrimaryExpr();
                break;
        }
    }

    void parsePrimaryExpr() throws SyntaxError {
        switch (currentToken.kind) {
            case Token.ID:
                parseIdent();
                parsePrimaryRest();
                break;
            case Token.LPAREN:
                accept();
                parseExpr();
                match(Token.RPAREN);
                break;
            case Token.INTLITERAL:
                parseIntLiteral();
                break;
            case Token.FLOATLITERAL:
                parseFloatLiteral();
                break;
            case Token.BOOLEANLITERAL:
                parseBooleanLiteral();
                break;
            case Token.STRINGLITERAL:
                parseStringLiteral();
                break;
            default:
                syntacticError("illegal primary expression", currentToken.spelling);
                break;
        }
    }
    void parsePrimaryRest() throws SyntaxError {
        if(currentToken.kind == Token.LPAREN) {
            parseArgList();
        } else if(currentToken.kind == Token.LBRACKET) {
            match(Token.LBRACKET);
            parseExpr();
            match(Token.RBRACKET);
        }
    }
    // ========================== LITERALS ========================
    // Calls these methods rather than accept(). In future assignments,
    // literal AST nodes will be constructed inside these methods.
    void parseIntLiteral() throws SyntaxError {
        if (currentToken.kind == Token.INTLITERAL) {
            accept();
        } else {
            syntacticError("integer literal expected here", "");
        }
    }

    void parseFloatLiteral() throws SyntaxError {
        if (currentToken.kind == Token.FLOATLITERAL) {
            accept();
        } else {
            syntacticError("float literal expected here", "");
        }
    }

    void parseBooleanLiteral() throws SyntaxError {
        if (currentToken.kind == Token.BOOLEANLITERAL) {
            accept();
        } else {
            syntacticError("boolean literal expected here", "");
        }
    }
    void parseStringLiteral() throws SyntaxError {
        if(currentToken.kind == Token.STRINGLITERAL) {
            accept();
        } else {
            syntacticError("string literal expected here","");
        }
    }

    //-------------------- paras --------------------
    void parseParaList() throws SyntaxError {
        match(Token.LPAREN);
        if(currentToken.kind != Token.RPAREN) {
            parseProperParaList();
        }
        match(Token.RPAREN);
    }

    void parseProperParaList() throws SyntaxError {
        parseParaDecl();
        while(currentToken.kind == Token.COMMA) {
            match(Token.COMMA);
            parseParaDecl();
        }
    }

    void parseParaDecl() throws SyntaxError {
        parseType();
        parseIdent();
        if(currentToken.kind == Token.LBRACKET) {
            match(Token.LBRACKET);
            if(currentToken.kind == Token.INTLITERAL){
                parseIntLiteral();
            }
            match(Token.RBRACKET);
        }
    }

    void parseArgList() throws SyntaxError {
        match(Token.LPAREN);
        if(currentToken.kind != Token.RPAREN) {
            parseProperArgList();
        }
        match(Token.RPAREN);
    }

    void parseProperArgList() throws SyntaxError {
        parseArg();
        while(currentToken.kind == Token.COMMA) {
            match(Token.COMMA);
            parseArg();
        }
    }

    void parseArg() throws SyntaxError {
        parseExpr();
    }


}
