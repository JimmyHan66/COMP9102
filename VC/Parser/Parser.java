/*
 * Parser.java
 *
 * Thu 06 Mar 2025 12:58:29 AEDT
 *
 * PLEASE COMPARE Recogniser.java PROVIDED IN ASSIGNMENT 2 AND Parser.java
 * PROVIDED BELOW TO UNDERSTAND HOW THE FORMER IS MODIFIED TO OBTAIN THE LATTER.
 *
 * This parser for a subset of the VC language is intended to
 *  demonstrate how to create the AST nodes, including (among others):
 *  (1) a list (of statements)
 *  (2) a function
 *  (3) a statement (which is an expression statement),
 *  (4) a unary expression
 *  (5) a binary expression
 *  (6) terminals (identifiers, integer literals and operators)
 *
 * In addition, it also demonstrates how to use the two methods start
 * and finish to determine the position information for the start and
 * end of a construct (known as a phrase) corresponding an AST node.
 *
 * NOTE THAT THE POSITION INFORMATION WILL NOT BE MARKED. HOWEVER, IT CAN BE
 * USEFUL TO DEBUG YOUR IMPLEMENTATION.
 *
 * Note that what is provided below is an implementation for a subset of VC
 * given below rather than VC itself. It provides a good starting point for you
 * to implement a parser for VC yourself, by modifying the parsing methods
 * provided (whenever necessary).
 *
 *
 * Alternatively, you are free to disregard the starter code entirely and
 * develop your own solution, as long as it adheres to the same public
 * interface.


program       -> func-decl
func-decl     -> type identifier "(" ")" compound-stmt
type          -> void
identifier    -> ID
// statements
compound-stmt -> "{" stmt* "}"
stmt          -> expr-stmt
expr-stmt     -> expr? ";"
// expressions
expr                -> additive-expr
additive-expr       -> multiplicative-expr
                    |  additive-expr "+" multiplicative-expr
                    |  additive-expr "-" multiplicative-expr
multiplicative-expr -> unary-expr
	            |  multiplicative-expr "*" unary-expr
	            |  multiplicative-expr "/" unary-expr
unary-expr          -> "-" unary-expr
		    |  primary-expr

primary-expr        -> identifier
 		    |  INTLITERAL
		    | "(" expr ")"
 */

package VC.Parser;

import VC.Scanner.Scanner;
import VC.Scanner.SourcePosition;
import VC.Scanner.Token;
import VC.ErrorReporter;
import VC.ASTs.*;

public class Parser {

  private Scanner scanner;
  private ErrorReporter errorReporter;
  private Token currentToken;
  private SourcePosition previousTokenPosition;
  private SourcePosition dummyPos = new SourcePosition();

  public Parser (Scanner lexer, ErrorReporter reporter) {
    scanner = lexer;
    errorReporter = reporter;

    previousTokenPosition = new SourcePosition();

    currentToken = scanner.getToken();
  }

// match checks to see f the current token matches tokenExpected.
// If so, fetches the next token.
// If not, reports a syntactic error.

  void match(int tokenExpected) throws SyntaxError {
    if (currentToken.kind == tokenExpected) {
      previousTokenPosition = currentToken.position;
      currentToken = scanner.getToken();
    } else {
      syntacticError("\"%\" expected here", Token.spell(tokenExpected));
    }
  }

  void accept() {
    previousTokenPosition = currentToken.position;
    currentToken = scanner.getToken();
  }

  void syntacticError(String messageTemplate, String tokenQuoted) throws SyntaxError {
    SourcePosition pos = currentToken.position;
    errorReporter.reportError(messageTemplate, tokenQuoted, pos);
    throw(new SyntaxError());
  }

// start records the position of the start of a phrase.
// This is defined to be the position of the first
// character of the first token of the phrase.

  void start(SourcePosition position) {
    position.lineStart = currentToken.position.lineStart;
    position.charStart = currentToken.position.charStart;
  }

// finish records the position of the end of a phrase.
// This is defined to be the position of the last
// character of the last token of the phrase.

  void finish(SourcePosition position) {
    position.lineFinish = previousTokenPosition.lineFinish;
    position.charFinish = previousTokenPosition.charFinish;
  }

  void copyStart(SourcePosition from, SourcePosition to) {
    to.lineStart = from.lineStart;
    to.charStart = from.charStart;
  }

// ========================== PROGRAMS ========================

  public Program parseProgram() {

    Program programAST = null;

    SourcePosition programPos = new SourcePosition();
    start(programPos);
    List dlAST = new EmptyDeclList(dummyPos);
    try {
        if (currentToken.kind != Token.EOF) {
            Type tAST = parseType();
            dlAST = parseDeclList(tAST);
        }
        match(Token.EOF);
        finish(programPos);
        programAST = new Program(dlAST, programPos);
    } catch (SyntaxError s) {
        return null;
    }
    return programAST;
  }

// ========================== DECLARATIONS ========================
// Compared to recogniser add linked list
List parseDeclList(Type tAST) throws SyntaxError {
    List dlAST = null;
    Decl dAST = null;
    Ident iAST = parseIdent();
    SourcePosition dlPos = new SourcePosition();
    start(dlPos);
    boolean hasComma = false;
    boolean isFunction = currentToken.kind == Token.LPAREN;
    if (isFunction) {
      dAST = parseFuncDecl(tAST, iAST, dlPos);
    } else {
      dAST = parseVarDeclNoList(tAST, iAST, dlPos, true);
      boolean hasMoreVars = currentToken.kind == Token.COMMA;
      if (hasMoreVars) {
        match(Token.COMMA);
        hasComma = true;
      } else {
        match(Token.SEMICOLON);
      }
    }

    if (isTypeToken(currentToken.kind)) {
      tAST = parseType();
      List restDeclList = parseDeclList(tAST);
      finish(dlPos);
      dlAST = new DeclList(dAST, restDeclList, dlPos);
    } else if(currentToken.kind == Token.ID && hasComma == true) {
      List restDeclList = parseDeclList(tAST);
      finish(dlPos);
      dlAST = new DeclList(dAST, restDeclList, dlPos);
    } else {
      finish(dlPos);
      dlAST = new DeclList(dAST, new EmptyDeclList(dummyPos), dlPos);
    }

    return (dlAST != null) ? dlAST : new EmptyDeclList(dummyPos);
  }


  private boolean isTypeToken(int tokenKind) {
  return tokenKind == Token.VOID || tokenKind == Token.BOOLEAN ||
         tokenKind == Token.INT || tokenKind == Token.FLOAT;
  }


  Decl parseVarDeclNoList(Type tAST, Ident iAST, SourcePosition dPos, boolean isGlobal) throws SyntaxError {
      Type typeCopy = tAST;

      if (currentToken.kind == Token.LBRACKET) {
        match(Token.LBRACKET);
        typeCopy = parseArrayType(typeCopy, dPos);
      }

      Expr initAST = new EmptyExpr(dummyPos);
      if (currentToken.kind == Token.EQ) {
        match(Token.EQ);
        initAST = parseInitialiser();
      }

      if (isGlobal) {
        return new GlobalVarDecl(typeCopy, iAST, initAST, dPos);
      } else {
        return new LocalVarDecl(typeCopy, iAST, initAST, dPos);
      }
    }
  Decl parseFuncDecl(Type tAST, Ident iAST, SourcePosition dPos) throws SyntaxError {
      Decl dAST = null;
      List fplAST = parseParaList();
      Stmt cAST = parseCompoundStmt();
      finish(dPos);
      dAST = new FuncDecl(tAST, iAST, fplAST, cAST, dPos);
      return dAST;
  }

  Expr parseInitialiser() throws SyntaxError {
    Expr initAST = null;
    SourcePosition initPos = new SourcePosition();
    start(initPos);
    if(currentToken.kind == Token.LCURLY) {
            match(Token.LCURLY);
            if(currentToken.kind == Token.RCURLY) {
              match(Token.RCURLY);
              finish(initPos);
              initAST = new ArrayInitExpr(new EmptyArrayExprList(dummyPos), initPos);
            } else {
              List aiAST = parseArrayExprList();
              match(Token.RCURLY);
              finish(initPos);
              initAST = new ArrayInitExpr(aiAST, initPos);
            }
    } else {
      initAST = parseExpr();
    }
    return initAST;
  }

  List parseArrayExprList() throws SyntaxError {
    List aiAST = null;
    SourcePosition aPos = new SourcePosition();
    start(aPos);
    Expr exprAST = parseExpr();
    if (currentToken.kind == Token.COMMA) {
      match(Token.COMMA);
      if (currentToken.kind == Token.RCURLY) {
        finish(aPos);
        aiAST = new ArrayExprList(exprAST, new EmptyArrayExprList(dummyPos), aPos);
      } else {
        aiAST = parseArrayExprList();
        finish(aPos);
        aiAST = new ArrayExprList(exprAST, aiAST, aPos);
      }
    } else {
      finish(aPos);
      aiAST = new ArrayExprList(exprAST, new EmptyArrayExprList(dummyPos), aPos);
    }
    return aiAST;
  }

  // Parse local variable declarations
  List parseLocalVarDeclList(Type tAST) throws SyntaxError {
    List dlAST = null;
    SourcePosition dlPos = new SourcePosition();
    start(dlPos);
    Ident iAST = parseIdent();
    boolean hasComma = false;
    Decl dAST = parseVarDeclNoList(tAST, iAST, dlPos, false);
    boolean hasMoreVars = currentToken.kind == Token.COMMA;
    if (hasMoreVars) {
      match(Token.COMMA);
      hasComma = true;
    } else {
      match(Token.SEMICOLON);
    }
    if (isTypeToken(currentToken.kind)) {
      tAST = parseType();
      List restDeclList = parseLocalVarDeclList(tAST);
      finish(dlPos);
      dlAST = new DeclList(dAST, restDeclList, dlPos);
    } else if(currentToken.kind == Token.ID && hasComma == true) {
      List restDeclList = parseLocalVarDeclList(tAST);
      finish(dlPos);
      dlAST = new DeclList(dAST, restDeclList, dlPos);
    } else {
      finish(dlPos);
      dlAST = new DeclList(dAST, new EmptyDeclList(dummyPos), dlPos);
    }

    return (dlAST != null) ? dlAST : new EmptyDeclList(dummyPos);

  }
//  ======================== TYPES ==========================

  Type parseType() throws SyntaxError {
    Type typeAST = null;

    SourcePosition typePos = new SourcePosition();
    start(typePos);
    switch (currentToken.kind) {
            case Token.VOID:
                match(Token.VOID);
                finish(typePos);
                typeAST = new VoidType(typePos);
                break;
            case Token.BOOLEAN:
                match(Token.BOOLEAN);
                finish(typePos);
                typeAST = new BooleanType(typePos);
                break;
            case Token.INT:
                match(Token.INT);
                finish(typePos);
                typeAST = new IntType(typePos);
                break;
            case Token.FLOAT:
                match(Token.FLOAT);
                finish(typePos);
                typeAST = new FloatType(typePos);
                break;
            default:
                syntacticError("\"%\" expected here", currentToken.spelling);
                break;
        }
    return typeAST;
    }

  Type parseArrayType(Type tAST, SourcePosition dPos) throws SyntaxError {
    Expr atAST = new EmptyExpr(dPos);
    if(currentToken.kind == Token.INTLITERAL) {
      atAST = new IntExpr(parseIntLiteral(), dPos);
    }
    match(Token.RBRACKET);
    tAST = new ArrayType(tAST, atAST,dPos);
    return tAST;
  }

// ======================= STATEMENTS ==============================

  Stmt parseCompoundStmt() throws SyntaxError {
    Stmt cAST = null;

    SourcePosition stmtPos = new SourcePosition();
    start(stmtPos);

    match(Token.LCURLY);

    // Insert code here to build a DeclList node for variable declarations
    List dlAST = new EmptyDeclList(dummyPos);
    if (isTypeToken(currentToken.kind)) {
        Type tAST = parseType();
        dlAST = parseLocalVarDeclList(tAST);
    }

    List slAST = parseStmtList();
    match(Token.RCURLY);
    finish(stmtPos);

    /* In the subset of the VC grammar, no variable declarations are
     * allowed. Therefore, a block is empty iff it has no statements.
     */
    if (dlAST instanceof EmptyStmtList && slAST  instanceof EmptyStmtList)
      cAST = new EmptyCompStmt(stmtPos);
    else
      cAST = new CompoundStmt(dlAST, slAST, stmtPos);
    return cAST;
  }

  List parseStmtList() throws SyntaxError {
    List slAST = null;

    SourcePosition stmtPos = new SourcePosition();
    start(stmtPos);

    if (currentToken.kind != Token.RCURLY) {
      Stmt sAST = parseStmt();
      {
        if (currentToken.kind != Token.RCURLY) {
          slAST = parseStmtList();
          finish(stmtPos);
          slAST = new StmtList(sAST, slAST, stmtPos);
        } else {
          finish(stmtPos);
          slAST = new StmtList(sAST, new EmptyStmtList(dummyPos), stmtPos);
        }
      }
    }
    else
      slAST = new EmptyStmtList(dummyPos);

    return slAST;
  }

  Stmt parseStmt() throws SyntaxError {
    Stmt sAST = null;
    switch (currentToken.kind) {
            case Token.LCURLY:
                sAST = parseCompoundStmt();
                break;
            case Token.IF:
                sAST = parseIfStmt();
                break;
            case Token.FOR:
                sAST = parseForStmt();
                break;
            case Token.WHILE:
                sAST = parseWhileStmt();
                break;
            case Token.BREAK:
                sAST = parseBreakStmt();
                break;
            case Token.RETURN:
                sAST = parseReturnStmt();
                break;
            case Token.CONTINUE:
                sAST = parseContinueStmt();
                break;
            default:
                sAST = parseExprStmt();
                break;
        }
    return sAST;
  }

  Stmt parseIfStmt() throws SyntaxError {
      Stmt sAST = null;
      SourcePosition sPos = new SourcePosition();
      start(sPos);
      match(Token.IF);
      match(Token.LPAREN);
      Expr eAST = parseExpr();
      match(Token.RPAREN);
      Stmt s1AST = parseStmt();

      if (currentToken.kind == Token.ELSE) {
        match(Token.ELSE);
        Stmt s2AST = parseStmt();
        finish(sPos);
        sAST = new IfStmt(eAST, s1AST, s2AST, sPos);
      } else {
        finish(sPos);
        sAST = new IfStmt(eAST, s1AST, sPos);
      }

      return sAST;

  }

  Stmt parseForStmt() throws SyntaxError {
    Stmt sAST = null;
    SourcePosition sPos = new SourcePosition();
    start(sPos);

    match(Token.FOR);
    match(Token.LPAREN);

    Expr e1AST = null;
    if (currentToken.kind != Token.SEMICOLON) {
      e1AST = parseExpr();
    } else {
      e1AST = new EmptyExpr(dummyPos);
    }
    match(Token.SEMICOLON);

    Expr e2AST = null;
    if (currentToken.kind != Token.SEMICOLON) {
      e2AST = parseExpr();
    } else {
      e2AST = new EmptyExpr(dummyPos);
    }
    match(Token.SEMICOLON);

    Expr e3AST = null;
    if (currentToken.kind != Token.RPAREN) {
      e3AST = parseExpr();
    } else {
      e3AST = new EmptyExpr(dummyPos);
    }
    match(Token.RPAREN);

    Stmt s3AST = parseStmt();
    finish(sPos);
    sAST = new ForStmt(e1AST, e2AST, e3AST, s3AST, sPos);

    return sAST;
  }

  Stmt parseWhileStmt() throws SyntaxError {
    Stmt sAST = null;
    SourcePosition sPos = new SourcePosition();
    start(sPos);

    match(Token.WHILE);
    match(Token.LPAREN);
    Expr eAST = parseExpr();
    match(Token.RPAREN);
    Stmt s1AST = parseStmt();
    finish(sPos);
    sAST = new WhileStmt(eAST, s1AST, sPos);

    return sAST;
  }

  Stmt parseBreakStmt() throws SyntaxError {
    Stmt sAST = null;
    SourcePosition sPos = new SourcePosition();
    start(sPos);

    match(Token.BREAK);
    match(Token.SEMICOLON);
    finish(sPos);
    sAST = new BreakStmt(sPos);

    return sAST;
  }

  Stmt parseContinueStmt() throws SyntaxError {
    Stmt sAST = null;
    SourcePosition sPos = new SourcePosition();
    start(sPos);

    match(Token.CONTINUE);
    match(Token.SEMICOLON);
    finish(sPos);
    sAST = new ContinueStmt(sPos);

    return sAST;
  }


Stmt parseReturnStmt() throws SyntaxError {
  Stmt sAST = null;
  SourcePosition sPos = new SourcePosition();
  start(sPos);

  match(Token.RETURN);
  Expr eAST = null;
  if (currentToken.kind != Token.SEMICOLON) {
    eAST = parseExpr();
  } else {
    eAST = new EmptyExpr(dummyPos);
  }

  match(Token.SEMICOLON);
  finish(sPos);
  sAST = new ReturnStmt(eAST, sPos);

  return sAST;
}

  Stmt parseExprStmt() throws SyntaxError {
    Stmt sAST = null;

    SourcePosition stmtPos = new SourcePosition();
    start(stmtPos);

    if (currentToken.kind != Token.SEMICOLON) {
        Expr eAST = parseExpr();
        match(Token.SEMICOLON);
        finish(stmtPos);
        sAST = new ExprStmt(eAST, stmtPos);

    } else {
      match(Token.SEMICOLON);
      finish(stmtPos);
      sAST = new ExprStmt(new EmptyExpr(dummyPos), stmtPos);
    }
    return sAST;
  }


// ======================= PARAMETERS =======================

  List parseParaList() throws SyntaxError {
    List formalsAST = null;

    SourcePosition formalsPos = new SourcePosition();
    start(formalsPos);

    match(Token.LPAREN);
    if(currentToken.kind != Token.RPAREN) {
      formalsAST = parseProperParaList();
      match(Token.RPAREN);
    } else {
      match(Token.RPAREN);
      finish(formalsPos);
      formalsAST = new EmptyParaList (formalsPos);
    }
    return formalsAST;
  }

  List parseProperParaList() throws SyntaxError {
    List formalsAST = null;
    SourcePosition formalsPos = new SourcePosition();
    start(formalsPos);

    ParaDecl pdAST = parseParaDecl();

    if (currentToken.kind == Token.COMMA) {
      match(Token.COMMA);
      List restFormalsAST = parseProperParaList();
      finish(formalsPos);
      formalsAST = new ParaList(pdAST, restFormalsAST, formalsPos);
    } else {
      finish(formalsPos);
      formalsAST = new ParaList(pdAST, new EmptyParaList(dummyPos), formalsPos);
    }

    return formalsAST;
  }

  ParaDecl parseParaDecl() throws SyntaxError {
    ParaDecl pdAST = null;
    SourcePosition paraPos = new SourcePosition();
    start(paraPos);

    Type tAST = parseType();
    Ident iAST = parseIdent();

    if (currentToken.kind == Token.LBRACKET) {
      match(Token.LBRACKET);
      tAST = parseArrayType(tAST, paraPos);
    }

    finish(paraPos);
    pdAST = new ParaDecl(tAST, iAST, paraPos);
    return pdAST;
  }

// ======================= EXPRESSIONS ======================


  Expr parseExpr() throws SyntaxError {
    Expr exprAST = null;
    exprAST = parseAssignExpr();
    return exprAST;
  }

  Expr parseAssignExpr() throws SyntaxError {
    Expr exprAST = null;

    SourcePosition exprPos = new SourcePosition();
    start(exprPos);

    exprAST = parseCondOrExpr();

    if (currentToken.kind == Token.EQ) {
      Operator opAST = acceptOperator();
      Expr e2AST = parseAssignExpr();
      SourcePosition assPos = new SourcePosition();
      copyStart(exprPos, assPos);
      finish(assPos);
      exprAST = new AssignExpr(exprAST, e2AST, assPos);
    }

    return exprAST;
  }


  Expr parseCondOrExpr() throws SyntaxError {
    Expr exprAST = null;

    SourcePosition exprPos = new SourcePosition();
    start(exprPos);

    exprAST = parseCondAndExpr();

    while (currentToken.kind == Token.OROR) {
      Operator opAST = acceptOperator();
      Expr e2AST = parseCondAndExpr();
      SourcePosition orPos = new SourcePosition();
      copyStart(exprPos, orPos);
      finish(orPos);
      exprAST = new BinaryExpr(exprAST, opAST, e2AST, orPos);
    }

    return exprAST;
  }

  Expr parseCondAndExpr() throws SyntaxError {
    Expr exprAST = null;

    SourcePosition exprPos = new SourcePosition();
    start(exprPos);

    exprAST = parseEqualityExpr();

    while (currentToken.kind == Token.ANDAND) {
      Operator opAST = acceptOperator();
      Expr e2AST = parseEqualityExpr();
      SourcePosition andPos = new SourcePosition();
      copyStart(exprPos, andPos);
      finish(andPos);
      exprAST = new BinaryExpr(exprAST, opAST, e2AST, andPos);
    }

    return exprAST;
  }

  Expr parseEqualityExpr() throws SyntaxError {
    Expr exprAST = null;

    SourcePosition exprPos = new SourcePosition();
    start(exprPos);

    exprAST = parseRelExpr();

    while (currentToken.kind == Token.EQEQ || currentToken.kind == Token.NOTEQ) {
      Operator opAST = acceptOperator();
      Expr e2AST = parseRelExpr();
      SourcePosition eqPos = new SourcePosition();
      copyStart(exprPos, eqPos);
      finish(eqPos);
      exprAST = new BinaryExpr(exprAST, opAST, e2AST, eqPos);
    }

    return exprAST;
  }

  Expr parseRelExpr() throws SyntaxError {
    Expr exprAST = null;

    SourcePosition exprPos = new SourcePosition();
    start(exprPos);

    exprAST = parseAdditiveExpr();

    while (currentToken.kind == Token.LT || currentToken.kind == Token.LTEQ ||
           currentToken.kind == Token.GT || currentToken.kind == Token.GTEQ) {
      Operator opAST = acceptOperator();
      Expr e2AST = parseAdditiveExpr();
      SourcePosition relPos = new SourcePosition();
      copyStart(exprPos, relPos);
      finish(relPos);
      exprAST = new BinaryExpr(exprAST, opAST, e2AST, relPos);
    }

    return exprAST;
  }


  Expr parseAdditiveExpr() throws SyntaxError {
    Expr exprAST = null;

    SourcePosition addStartPos = new SourcePosition();
    start(addStartPos);

    exprAST = parseMultiplicativeExpr();
    while (currentToken.kind == Token.PLUS
           || currentToken.kind == Token.MINUS) {
      Operator opAST = acceptOperator();
      Expr e2AST = parseMultiplicativeExpr();

      SourcePosition addPos = new SourcePosition();
      copyStart(addStartPos, addPos);
      finish(addPos);
      exprAST = new BinaryExpr(exprAST, opAST, e2AST, addPos);
    }
    return exprAST;
  }

  Expr parseMultiplicativeExpr() throws SyntaxError {

    Expr exprAST = null;

    SourcePosition multStartPos = new SourcePosition();
    start(multStartPos);

    exprAST = parseUnaryExpr();
    while (currentToken.kind == Token.MULT
           || currentToken.kind == Token.DIV) {
      Operator opAST = acceptOperator();
      Expr e2AST = parseUnaryExpr();
      SourcePosition multPos = new SourcePosition();
      copyStart(multStartPos, multPos);
      finish(multPos);
      exprAST = new BinaryExpr(exprAST, opAST, e2AST, multPos);
    }
    return exprAST;
  }

  Expr parseUnaryExpr() throws SyntaxError {

    Expr exprAST = null;

    SourcePosition unaryPos = new SourcePosition();
    start(unaryPos);

    switch (currentToken.kind) {
      case Token.MINUS:
      case Token.PLUS:
      case Token.NOT:
        {
          Operator opAST = acceptOperator();
          Expr e2AST = parseUnaryExpr();
          finish(unaryPos);
          exprAST = new UnaryExpr(opAST, e2AST, unaryPos);
        }
        break;

      default:
        exprAST = parsePrimaryExpr();
        break;

    }
    return exprAST;
  }

  Expr parsePrimaryExpr() throws SyntaxError {

    Expr exprAST = null;

    SourcePosition primPos = new SourcePosition();
    start(primPos);

    switch (currentToken.kind) {

      case Token.ID:
        Ident iAST = parseIdent();
        exprAST = parsePrimaryRest(iAST, primPos);
        break;
      case Token.LPAREN:
        {
          accept();
          exprAST = parseExpr();
	        match(Token.RPAREN);
        }
        break;

      case Token.INTLITERAL:
        IntLiteral ilAST = parseIntLiteral();
        finish(primPos);
        exprAST = new IntExpr(ilAST, primPos);
        break;
      case Token.FLOATLITERAL:
        FloatLiteral flAST = parseFloatLiteral();
        finish(primPos);
        exprAST = new FloatExpr(flAST, primPos);
        break;

      case Token.BOOLEANLITERAL:
        BooleanLiteral blAST = parseBooleanLiteral();
        finish(primPos);
        exprAST = new BooleanExpr(blAST, primPos);
        break;
      case Token.STRINGLITERAL:
        StringLiteral slAST = parseStringLiteral();
        finish(primPos);
        exprAST = new StringExpr(slAST, primPos);
        break;

      default:
        syntacticError("illegal primary expression", currentToken.spelling);

    }
    return exprAST;
  }

  Expr parsePrimaryRest(Ident iAST, SourcePosition primPos) throws SyntaxError {
    Expr exprAST = null;

    if (currentToken.kind == Token.LPAREN) {
      match(Token.LPAREN);
      List aplAST = parseArgList();
      match(Token.RPAREN);
      finish(primPos);
      exprAST = new CallExpr(iAST, aplAST, primPos);
    } else if (currentToken.kind == Token.LBRACKET) {
      match(Token.LBRACKET);
      Expr indexExpr = parseExpr();
      match(Token.RBRACKET);
      finish(primPos);
      Var simVAST = new SimpleVar(iAST, primPos);
      exprAST = new ArrayExpr(simVAST, indexExpr, primPos);
    } else {
      finish(primPos);
      Var simVAST = new SimpleVar(iAST, primPos);
      exprAST = new VarExpr(simVAST, primPos);
    }

    return exprAST;
  }

// Arguments:
  List parseArgList() throws SyntaxError {
    List argsAST = null;
    SourcePosition argsPos = new SourcePosition();
    start(argsPos);

    if(currentToken.kind != Token.RPAREN) {
      argsAST = parseProperArgList();
    } else {
      finish(argsPos);
      argsAST = new EmptyArgList(argsPos);
    }

    return argsAST;
  }

  List parseProperArgList() throws SyntaxError {
    List argsAST = null;
    SourcePosition argsPos = new SourcePosition();
    start(argsPos);

    Arg argAST = parseArg();

    if (currentToken.kind == Token.COMMA) {
      match(Token.COMMA);
      List raAST = parseProperArgList();
      finish(argsPos);
      argsAST = new ArgList(argAST, raAST, argsPos);
    } else {
      finish(argsPos);
      argsAST = new ArgList(argAST, new EmptyArgList(dummyPos), argsPos);
    }

    return argsAST;
  }

  Arg parseArg() throws SyntaxError {
    Arg argAST = null;
    SourcePosition argPos = new SourcePosition();
    start(argPos);

    Expr exprAST = parseExpr();

    finish(argPos);
    argAST = new Arg(exprAST, argPos);
    return argAST;
  }


// ========================== ID, OPERATOR and LITERALS ========================

  Ident parseIdent() throws SyntaxError {

    Ident I = null;

    if (currentToken.kind == Token.ID) {
      previousTokenPosition = currentToken.position;
      String spelling = currentToken.spelling;
      I = new Ident(spelling, previousTokenPosition);
      currentToken = scanner.getToken();
    } else
      syntacticError("identifier expected here", "");
    return I;
  }

// acceptOperator parses an operator, and constructs a leaf AST for it

  Operator acceptOperator() throws SyntaxError {
    Operator O = null;

    previousTokenPosition = currentToken.position;
    String spelling = currentToken.spelling;
    O = new Operator(spelling, previousTokenPosition);
    currentToken = scanner.getToken();
    return O;
  }


  IntLiteral parseIntLiteral() throws SyntaxError {
    IntLiteral IL = null;

    if (currentToken.kind == Token.INTLITERAL) {
      String spelling = currentToken.spelling;
      accept();
      IL = new IntLiteral(spelling, previousTokenPosition);
    } else
      syntacticError("integer literal expected here", "");
    return IL;
  }

  FloatLiteral parseFloatLiteral() throws SyntaxError {
    FloatLiteral FL = null;

    if (currentToken.kind == Token.FLOATLITERAL) {
      String spelling = currentToken.spelling;
      accept();
      FL = new FloatLiteral(spelling, previousTokenPosition);
    } else
      syntacticError("float literal expected here", "");
    return FL;
  }

  BooleanLiteral parseBooleanLiteral() throws SyntaxError {
    BooleanLiteral BL = null;

    if (currentToken.kind == Token.BOOLEANLITERAL) {
      String spelling = currentToken.spelling;
      accept();
      BL = new BooleanLiteral(spelling, previousTokenPosition);
    } else
      syntacticError("boolean literal expected here", "");
    return BL;
  }

  StringLiteral parseStringLiteral() throws SyntaxError {
    StringLiteral SL = null;

    if (currentToken.kind == Token.STRINGLITERAL) {
        String spelling = currentToken.spelling;
        accept();
        SL = new StringLiteral(spelling, previousTokenPosition);
    } else
        syntacticError("string literal expected here", "");
    return SL;
    }
}

