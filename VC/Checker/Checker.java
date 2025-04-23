/*
 * Checker.java
 *
 * This VC compiler pass is responsible for performing semantic analysis 
 * on the abstract syntax tree (AST) of a VC program. It checks for scope and 
 * type rules, decorates the AST with type information, and links identifiers 
 * to their declarations.
 *
 * Sun 09 Mar 2025 08:44:27 AEDT
 *
 */

 package VC.Checker;

 import VC.ASTs.*;
 import VC.Scanner.SourcePosition;
 import VC.ErrorReporter;
 import VC.StdEnvironment;
 
 import java.util.Objects;
 import java.util.Optional;
 import java.util.List;
 import java.util.LinkedList;
 
 public final class Checker implements Visitor {
 
     // Enum for error messages
     private enum ErrorMessage {
         MISSING_MAIN("*0: main function is missing"),
 
         // Defined occurrences of identifiers (global, local, and parameters)
         MAIN_RETURN_TYPE_NOT_INT("*1: return type of main is not int"),
         IDENTIFIER_REDECLARED("*2: identifier redeclared"),
         IDENTIFIER_DECLARED_VOID("*3: identifier declared void"),
         IDENTIFIER_DECLARED_VOID_ARRAY("*4: identifier declared void[]"),
 
 
         // applied occurrences of identifiers
         IDENTIFIER_UNDECLARED("*5: identifier undeclared"),
     
         // assignments
         INCOMPATIBLE_TYPE_FOR_ASSIGNMENT("*6: incompatible type for ="),
         INVALID_LVALUE_IN_ASSIGNMENT("*7: invalid lvalue in assignment"),
 
 
         // types for expressions 
         INCOMPATIBLE_TYPE_FOR_RETURN("*8: incompatible type for return"),
         INCOMPATIBLE_TYPE_FOR_BINARY_OPERATOR("*9: incompatible type for this binary operator"),
         INCOMPATIBLE_TYPE_FOR_UNARY_OPERATOR("*10: incompatible type for this unary operator"),
 
 
     // scalars
         ARRAY_FUNCTION_AS_SCALAR("*11: attempt to use an array/function as a scalar"),
 
     // arrays
         SCALAR_FUNCTION_AS_ARRAY("*12: attempt to use a scalar/function as an array"),
         WRONG_TYPE_FOR_ARRAY_INITIALISER("*13: wrong type for element in array initialiser"),
         INVALID_INITIALISER_ARRAY_FOR_SCALAR("*14: invalid initialiser: array initialiser for scalar"),
         INVALID_INITIALISER_SCALAR_FOR_ARRAY("*15: invalid initialiser: scalar initialiser for array"),
         EXCESS_ELEMENTS_IN_ARRAY_INITIALISER("*16: excess elements in array initialiser"),
         ARRAY_SUBSCRIPT_NOT_INTEGER("*17: array subscript is not an integer"),
         ARRAY_SIZE_MISSING("*18: array size missing"),
 
     // functions
         SCALAR_ARRAY_AS_FUNCTION("*19: attempt to reference a scalar/array as a function"),
 
         // conditional expressions in if, for and while
         IF_CONDITIONAL_NOT_BOOLEAN("*20: if conditional is not boolean"),
         FOR_CONDITIONAL_NOT_BOOLEAN("*21: for conditional is not boolean"),
         WHILE_CONDITIONAL_NOT_BOOLEAN("*22: while conditional is not boolean"),
 
         // break and continue
         BREAK_NOT_IN_LOOP("*23: break must be in a while/for"),
         CONTINUE_NOT_IN_LOOP("*24: continue must be in a while/for"),
 
     // parameters
         TOO_MANY_ACTUAL_PARAMETERS("*25: too many actual parameters"),
         TOO_FEW_ACTUAL_PARAMETERS("*26: too few actual parameters"),
         WRONG_TYPE_FOR_ACTUAL_PARAMETER("*27: wrong type for actual parameter"),
 
         // reserved for errors that I may have missed (J. Xue)
         MISC_1("*28: misc 1"),
         MISC_2("*29: misc 2"),
 
 
         // the following two checks are optional 
         STATEMENTS_NOT_REACHED("*30: statement(s) not reached"),
         MISSING_RETURN_STATEMENT("*31: missing return statement");
 
         private final String message;
 
         ErrorMessage(String message) {
             this.message = message;
         }
 
         public String getMessage() {
             return message;
         }
     }
 
     private final SymbolTable idTable;
     private static final SourcePosition dummyPos = new SourcePosition();
     private final ErrorReporter reporter;
     private int depth = 0;
     private boolean curFuncReturn;
 
     public Checker(ErrorReporter reporter) {
         this.reporter = Objects.requireNonNull(reporter, "ErrorReporter must not be null");
         this.idTable = new SymbolTable();
         this.depth = 0;
         this.curFuncReturn = true;
         establishStdEnvironment();
     }
 
     /* Auxiliary Methods */
 
      /* 
       * Declares a variable in the symbol table and checks for redeclaration errors.
       */
 
     private void declareVariable(Ident ident, Decl decl) {
         idTable.retrieveOneLevel(ident.spelling).ifPresent(entry -> 
             reporter.reportError(ErrorMessage.IDENTIFIER_REDECLARED.getMessage(), ident.spelling, ident.position)
         );
         idTable.insert(ident.spelling, decl);
         ident.visit(this, null);
     }
 
     // Your other auxilary methods
 
     public void check(AST ast) {
         ast.visit(this, null);
     }
 
     // Programs
 
     @Override
     public Object visitProgram(Program ast, Object o) {
         ast.FL.visit(this, null);
 
         idTable.retrieve("main")
             .filter(entry -> entry.attr instanceof FuncDecl)
             .map(entry -> entry.attr)
             .ifPresentOrElse(
                 mainDecl -> {
                     if (!mainDecl.T.equals(StdEnvironment.intType)) {
                         reporter.reportError(ErrorMessage.MAIN_RETURN_TYPE_NOT_INT.getMessage(), "", ast.position);
                     }
                 },
                 () -> reporter.reportError(ErrorMessage.MISSING_MAIN.getMessage(), "", ast.position)
             );
 
         return null;
     }
 
     // Statements
 
     @Override
     public Object visitCompoundStmt(CompoundStmt ast, Object o) {
         boolean isFunctionBody = (ast.parent instanceof FuncDecl);
         
         if (!isFunctionBody) {
             idTable.openScope();
         }
         
         ast.DL.visit(this, o);
         ast.SL.visit(this, o);
         
         if (!isFunctionBody) {
             idTable.closeScope();
         }
         
         return null;
     }
 
     @Override
     public Object visitStmtList(StmtList ast, Object o) {
         ast.S.visit(this, o);
         if (ast.S instanceof ReturnStmt && ast.SL instanceof StmtList) {
             reporter.reportError(ErrorMessage.STATEMENTS_NOT_REACHED.getMessage(), "", ast.SL.position);
         }
         ast.SL.visit(this, o);
         return null;
     }
 
     @Override
     public Object visitExprStmt(ExprStmt ast, Object o) {
         ast.E.visit(this, o);
         return null;
     }
 
 
     @Override
     public Object visitEmptyStmt(EmptyStmt ast, Object o) {
         return null;
     }
 
     @Override
     public Object visitEmptyStmtList(EmptyStmtList ast, Object o) {
         return null;
     }
 
     @Override
     public Object visitEmptyCompStmt(EmptyCompStmt ast, Object o) {
         return null;
     }
 
     @Override
     public Object visitIfStmt(IfStmt ast, Object o) {
         Type t = (Type) ast.E.visit(this, o);
         
         if (!t.equals(StdEnvironment.booleanType)) {
             reporter.reportError(ErrorMessage.IF_CONDITIONAL_NOT_BOOLEAN.getMessage(), "", ast.E.position);
         }
         
         ast.S1.visit(this, o);
         if (ast.S2 != null) {
             ast.S2.visit(this, o);
         }
         
         return null;
     }
     
     @Override
     public Object visitWhileStmt(WhileStmt ast, Object o) {
         Type t = (Type) ast.E.visit(this, o);
         
         if (!t.equals(StdEnvironment.booleanType)) {
             reporter.reportError(ErrorMessage.WHILE_CONDITIONAL_NOT_BOOLEAN.getMessage(), "", ast.E.position);
         }
         
         depth++;
         ast.S.visit(this, o);
         depth--;
         
         return null;
     }
     
     @Override
     public Object visitForStmt(ForStmt ast, Object o) {
         if (ast.E1 != null) {
             ast.E1.visit(this, o);
         }
         
         if (ast.E2 != null) {
             Type t = (Type) ast.E2.visit(this, o);
             if (!t.equals(StdEnvironment.booleanType)) {
                 reporter.reportError(ErrorMessage.FOR_CONDITIONAL_NOT_BOOLEAN.getMessage(), "", ast.E2.position);
             }
         }
         
         if (ast.E3 != null) {
             ast.E3.visit(this, o);
         }
         
         depth++;
         ast.S.visit(this, o);
         depth--;
         
         return null;
     }
     
     @Override
     public Object visitBreakStmt(BreakStmt ast, Object o) {
         if (depth == 0) {
             reporter.reportError(ErrorMessage.BREAK_NOT_IN_LOOP.getMessage(), "", ast.position);
         }
         return null;
     }
     
     @Override
     public Object visitContinueStmt(ContinueStmt ast, Object o) {
         if (depth == 0) {
             reporter.reportError(ErrorMessage.CONTINUE_NOT_IN_LOOP.getMessage(), "", ast.position);
         }
         return null;
     }
 
     @Override
     public Object visitReturnStmt(ReturnStmt ast, Object o) {
         curFuncReturn = true;
     
         FuncDecl currentFunc = null;
         if (o instanceof FuncDecl) {
             currentFunc = (FuncDecl) o;
         }
     
         if (currentFunc == null) {
             if (ast.E != null) {
                 ast.E.visit(this, o);
             }
             return null;
         }
         Type t1 = currentFunc.T;
         
         if(ast.E == null) {
             checkEmptyReturn(ast, t1);
         } else {
             checkValueReturn(ast, t1);
         }
         return null;
     }
     
     
     private void checkEmptyReturn(ReturnStmt ast, Type t1) {
         if (!t1.equals(StdEnvironment.voidType)) {
             reporter.reportError(ErrorMessage.INCOMPATIBLE_TYPE_FOR_RETURN.getMessage(), "", ast.position);
         }
     }
     
     private void checkValueReturn(ReturnStmt ast, Type t1) {
         Type t2 = (Type) ast.E.visit(this, null);
         
         if (!t1.assignable(t2)) {
             reporter.reportError(ErrorMessage.INCOMPATIBLE_TYPE_FOR_RETURN.getMessage(), "", ast.position
             );
         } 
         else if (t1.equals(StdEnvironment.floatType) && t2.equals(StdEnvironment.intType)) {
             ast.E = createI2f(ast.E);
         }
     }
     
     private Expr createI2f(Expr expr) {
         Operator op = new Operator("i2f", dummyPos);
         UnaryExpr e = new UnaryExpr(op, expr, dummyPos);
         e.type = StdEnvironment.floatType;
         return e;
     }
 
 
     // Expressions
     @Override
     public Object visitEmptyExpr(EmptyExpr ast, Object o) {
         ast.type = (ast.parent instanceof ReturnStmt) ? StdEnvironment.voidType : StdEnvironment.errorType;
         return ast.type;
     }
 
     @Override
     public Object visitBooleanExpr(BooleanExpr ast, Object o) {
         ast.type = StdEnvironment.booleanType;
         return ast.type;
     }
 
     @Override
     public Object visitIntExpr(IntExpr ast, Object o) {
         ast.type = StdEnvironment.intType;
         return ast.type;
     }
 
     @Override
     public Object visitFloatExpr(FloatExpr ast, Object o) {
         ast.type = StdEnvironment.floatType;
         return ast.type;
     }
 
     @Override
     public Object visitVarExpr(VarExpr ast, Object o) {
        ast.type = (Type) ast.V.visit(this, ast.parent);
        return ast.type;
     }
 
     @Override
     public Object visitStringExpr(StringExpr ast, Object o) {
         ast.type = StdEnvironment.stringType;
         return ast.type;
     }
  
     @Override
     public Object visitUnaryExpr(UnaryExpr ast, Object o) {
         Type t1 = (Type) ast.E.visit(this, o);
         String op = ast.O.spelling;
         
         switch (op) {
             case "i2f":
                 return handleI2fu(ast, t1);
                 
             case "!":
                 return handleNotu(ast, t1);
                 
             case "+":
             case "-":
                 return handleArithu(ast, t1, op);
                 
             default:
                 reporter.reportError(ErrorMessage.INCOMPATIBLE_TYPE_FOR_UNARY_OPERATOR.getMessage(), "", ast.position);
                 ast.type = StdEnvironment.errorType;
                 return ast.type;
         }
     }
     
     private Type handleI2fu(UnaryExpr ast, Type t1) {
         if (t1.isErrorType()) {
             ast.type = StdEnvironment.errorType;
             return ast.type;
         }
         if (t1.equals(StdEnvironment.intType)) {
             ast.type = StdEnvironment.floatType;
         } else {
             ast.type = t1; 
         }
         return ast.type;
     }
     
     private Type handleNotu(UnaryExpr ast, Type t1) {
         if (t1.isErrorType()) {
             ast.type = StdEnvironment.errorType;
             return ast.type;
         }
         if (t1.equals(StdEnvironment.booleanType)) {
             ast.type = StdEnvironment.booleanType;
             ast.O.spelling = "i" + ast.O.spelling; 
         } else {
             reporter.reportError(ErrorMessage.INCOMPATIBLE_TYPE_FOR_UNARY_OPERATOR.getMessage(), "", ast.position);
             ast.type = StdEnvironment.errorType;
         }
         return ast.type;
     }
     
     private Type handleArithu(UnaryExpr ast, Type t1, String op) {
         if (t1.isErrorType()) {
             ast.type = StdEnvironment.errorType;
             return ast.type;
         }
         if (t1.equals(StdEnvironment.intType)) {
             ast.type = StdEnvironment.intType;
             ast.O.spelling = "i" + op;
         } else if (t1.equals(StdEnvironment.floatType)) {
             ast.type = StdEnvironment.floatType;
             ast.O.spelling = "f" + op;
         } else {
             reporter.reportError(ErrorMessage.INCOMPATIBLE_TYPE_FOR_UNARY_OPERATOR.getMessage(), "", ast.position);
             ast.type = StdEnvironment.errorType;
         }
         return ast.type;
     }
 
 
 
     @Override
     public Object visitBinaryExpr(BinaryExpr ast, Object o) {
         Type t1 = (Type) ast.E1.visit(this, o);
         Type t2 = (Type) ast.E2.visit(this, o);
         String op = ast.O.spelling;
         boolean hasArrayError = false;
         
         if (t1.isArrayType() || t2.isArrayType()) {
             reporter.reportError(ErrorMessage.ARRAY_FUNCTION_AS_SCALAR.getMessage(), "", ast.position);
             hasArrayError = true;
         }
         
         Type result;
         switch (op) {
             case "&&":
             case "||":
                 result = handleLogic(ast, t1, t2);
                 break;
                 
             case "==":
             case "!=":
                 result = handleEQ(ast, t1, t2);
                 break;
                 
             case "<":
             case "<=":
             case ">":
             case ">=":
                 result = handleCompare(ast, t1, t2);
                 break;
                 
             case "+":
             case "-":
             case "*":
             case "/":
                 result = handleArith(ast, t1, t2);
                 break;
                 
             default:
                 reporter.reportError(ErrorMessage.INCOMPATIBLE_TYPE_FOR_BINARY_OPERATOR.getMessage(), "", ast.position);
                 result = StdEnvironment.errorType;
         }
         
         if (hasArrayError) {
             ast.type = StdEnvironment.errorType;
             return ast.type;
         }
         
         ast.type = result;
         return ast.type;
     }
     
     private Type handleLogic(BinaryExpr ast, Type t1, Type t2) {
         if (t1 instanceof ErrorType || t2 instanceof ErrorType) {
             ast.type = StdEnvironment.errorType;
             return ast.type;
         }
         if (!t1.equals(StdEnvironment.booleanType) || !t2.equals(StdEnvironment.booleanType)) {
             reporter.reportError(ErrorMessage.INCOMPATIBLE_TYPE_FOR_BINARY_OPERATOR.getMessage(), "", ast.position);
             ast.type = StdEnvironment.errorType;
         } else {
             ast.type = StdEnvironment.booleanType;
             ast.O.spelling = "i" + ast.O.spelling;
         }
         return ast.type;
     }
     
     
     private Type handleEQ(BinaryExpr ast, Type t1, Type t2) {
         if (t1 instanceof ErrorType || t2 instanceof ErrorType) {
             ast.type = StdEnvironment.errorType;
             return ast.type;
         }
         ast.type = StdEnvironment.booleanType;
         if (t1.equals(t2)) {
             if (t1.equals(StdEnvironment.floatType)) {
                 ast.O.spelling = "f" + ast.O.spelling;
             } else {
                 ast.O.spelling = "i" + ast.O.spelling;
             }
         } else if (t1.equals(StdEnvironment.intType) && t2.equals(StdEnvironment.floatType)) {
             ast.E1 = createI2f(ast.E1);
             ast.O.spelling = "f" + ast.O.spelling;
         } else if (t1.equals(StdEnvironment.floatType) && t2.equals(StdEnvironment.intType)) {
             ast.E2 = createI2f(ast.E2);
             ast.O.spelling = "f" + ast.O.spelling;
         } else {
             reporter.reportError(ErrorMessage.INCOMPATIBLE_TYPE_FOR_BINARY_OPERATOR.getMessage(), "", ast.position);
             ast.type = StdEnvironment.errorType;
         }
         return ast.type;
     }
     
     private Type handleCompare(BinaryExpr ast, Type t1, Type t2) {
         if (t1 instanceof ErrorType || t2 instanceof ErrorType) {
             ast.type = StdEnvironment.errorType;
             return ast.type;
         }
         if ((t1.equals(StdEnvironment.intType) || t1.equals(StdEnvironment.floatType)) &&
             (t2.equals(StdEnvironment.intType) || t2.equals(StdEnvironment.floatType))) {
             ast.type = StdEnvironment.booleanType;
             
             if (t1.equals(StdEnvironment.floatType) || t2.equals(StdEnvironment.floatType)) {
                 ast.O.spelling = "f" + ast.O.spelling;
                 
                 if (t1.equals(StdEnvironment.intType)) {
                     ast.E1 = createI2f(ast.E1);
                 } else if (t2.equals(StdEnvironment.intType)) {
                     ast.E2 = createI2f(ast.E2);
                 }
             } else {
                 ast.O.spelling = "i" + ast.O.spelling;
             }
         } else {
             reporter.reportError(ErrorMessage.INCOMPATIBLE_TYPE_FOR_BINARY_OPERATOR.getMessage(), "", ast.position);
             ast.type = StdEnvironment.errorType;
         }
         return ast.type;
     }
     
     private Type handleArith(BinaryExpr ast, Type t1, Type t2) {
         if (t1 instanceof ErrorType || t2 instanceof ErrorType) {
             ast.type = StdEnvironment.errorType;
             return ast.type;
         }  
         if ((t1.equals(StdEnvironment.intType) || t1.equals(StdEnvironment.floatType)) &&
             (t2.equals(StdEnvironment.intType) || t2.equals(StdEnvironment.floatType))) {
             if (t1.equals(StdEnvironment.floatType) || t2.equals(StdEnvironment.floatType)) {
                 ast.type = StdEnvironment.floatType;
                 ast.O.spelling = "f" + ast.O.spelling;
                 
                 if (t1.equals(StdEnvironment.intType)) {
                     ast.E1 = createI2f(ast.E1);
                 } else if (t2.equals(StdEnvironment.intType)) {
                     ast.E2 = createI2f(ast.E2);
                 }
             } else {
                 ast.type = StdEnvironment.intType;
                 ast.O.spelling = "i" + ast.O.spelling;
             }
         } else {
             reporter.reportError(ErrorMessage.INCOMPATIBLE_TYPE_FOR_BINARY_OPERATOR.getMessage(), "", ast.position);
             ast.type = StdEnvironment.errorType;
         }
         return ast.type;
     }
 
     @Override
     public Object visitAssignExpr(AssignExpr ast, Object o) {
        
        if(!(ast.E1 instanceof VarExpr || ast.E1 instanceof ArrayExpr)) {
            reporter.reportError(ErrorMessage.INVALID_LVALUE_IN_ASSIGNMENT.getMessage(), "", ast.position);
            ast.type = StdEnvironment.errorType;
            return ast.type;
        }
         Type t1 = (Type) ast.E1.visit(this, o);
         Type t2 = (Type) ast.E2.visit(this, o);
         
         if (ast.E1 instanceof VarExpr) {
             VarExpr varExpr = (VarExpr) ast.E1;
             if (varExpr.V instanceof SimpleVar) {
                 SimpleVar simpleVar = (SimpleVar) varExpr.V;
                 Ident ident = (Ident) simpleVar.I;
                 if (ident.decl instanceof FuncDecl) {
                     reporter.reportError(ErrorMessage.INVALID_LVALUE_IN_ASSIGNMENT.getMessage(), "", ast.position);
                     ast.type = StdEnvironment.errorType;
                     return ast.type;
                 }
             }
         }
         
         if (t1.isArrayType() && !(ast.E1 instanceof ArrayExpr)) {
             reporter.reportError(ErrorMessage.ARRAY_FUNCTION_AS_SCALAR.getMessage(), "", ast.position);
             ast.type = StdEnvironment.errorType;
             return ast.type;
         }
         
         if (t1.assignable(t2)) {
             ast.type = t1;
             
             if (t1.equals(StdEnvironment.floatType) && t2.equals(StdEnvironment.intType)) {
                 ast.E2 = createI2f(ast.E2);
             }
         } else {
             reporter.reportError(ErrorMessage.INCOMPATIBLE_TYPE_FOR_ASSIGNMENT.getMessage(), "", ast.position);
             ast.type = StdEnvironment.errorType;
         }
         
         return ast.type;
     }
 
     @Override
     public Object visitCallExpr(CallExpr ast, Object o) {
         ast.I.visit(this, null);
         AST d = ((Ident) ast.I).decl;
         
         if (ast.I.spelling.equals("main")) {
             reporter.reportError(ErrorMessage.MISC_1.getMessage() + ": main cannot be called", "", ast.position);
             ast.type = StdEnvironment.errorType;
             return ast.type;
         }
         
         if (d == null) {
             reporter.reportError(ErrorMessage.IDENTIFIER_UNDECLARED.getMessage(), ast.I.spelling, ast.position);
             ast.type = StdEnvironment.errorType;
             return ast.type;
         }
         
         if (!(d instanceof FuncDecl)) {
             reporter.reportError(ErrorMessage.SCALAR_ARRAY_AS_FUNCTION.getMessage(), ast.I.spelling, ast.position);
             ast.type = StdEnvironment.errorType;
             return ast.type;
         }
         
         FuncDecl fD = (FuncDecl) d;
         ast.type = (fD.T != null) ? fD.T : StdEnvironment.errorType;
         
         ast.AL.visit(this, fD.PL);
         
         return ast.type;
     }
     
     @Override
     public Object visitArgList(ArgList ast, Object o) {
         if (o instanceof EmptyParaList) {
             reporter.reportError(ErrorMessage.TOO_MANY_ACTUAL_PARAMETERS.getMessage(), "", ast.position);
             return null;
         }
         
         ast.A.visit(this, ((ParaList) o).P);
         
         AST remainingParams = ((ParaList) o).PL;
         AST remainingArgs = ast.AL;

         if (remainingParams instanceof ParaList && !(remainingArgs instanceof ArgList)) {
             reporter.reportError(ErrorMessage.TOO_FEW_ACTUAL_PARAMETERS.getMessage(), "", ast.position);
         } 
         else if (!(remainingParams instanceof ParaList) && remainingArgs instanceof ArgList) {
             reporter.reportError(ErrorMessage.TOO_MANY_ACTUAL_PARAMETERS.getMessage(), "", ast.position);
         } 
         else if (remainingParams instanceof ParaList && remainingArgs instanceof ArgList) {
             remainingArgs.visit(this, remainingParams);
         }
         
         return null;
     }
     
     @Override
     public Object visitEmptyArgList(EmptyArgList ast, Object o) {
         if (o instanceof ParaList) {
             reporter.reportError(ErrorMessage.TOO_FEW_ACTUAL_PARAMETERS.getMessage(), "", ast.position);
         }
         return null;
     }
     
     @Override
     public Object visitArg(Arg ast, Object o) {
         if (!(o instanceof ParaDecl)) {
             return ast.E.visit(this, null);
         }
         
         ParaDecl paramDecl = (ParaDecl) o;
         Type paramType = paramDecl.T;
         Type argType = (Type) ast.E.visit(this, null);
         
         boolean isParamArray = paramType.isArrayType();
         boolean isArgArray = argType.isArrayType();
         
         if (isParamArray != isArgArray) {
             reporter.reportError(ErrorMessage.WRONG_TYPE_FOR_ACTUAL_PARAMETER.getMessage(), "", ast.position);
         }
         else if (isParamArray) {
             ArrayType pArrayType = (ArrayType) paramType;
             ArrayType aArrayType = (ArrayType) argType;
             
             if (pArrayType.T == null || aArrayType.T == null || !pArrayType.T.equals(aArrayType.T)) {
                 reporter.reportError(ErrorMessage.WRONG_TYPE_FOR_ACTUAL_PARAMETER.getMessage(), "", ast.position);
             }
         }
         else if (!paramType.equals(argType)) {
             if (paramType.equals(StdEnvironment.floatType) && argType.equals(StdEnvironment.intType)) {
                 ast.E = createI2f(ast.E); 
             } 
             else if (paramType.equals(StdEnvironment.intType) && argType.equals(StdEnvironment.floatType)) {
                 reporter.reportError(ErrorMessage.WRONG_TYPE_FOR_ACTUAL_PARAMETER.getMessage(), "", ast.position);
             }
             else if (!paramType.assignable(argType)) {
                 reporter.reportError(ErrorMessage.WRONG_TYPE_FOR_ACTUAL_PARAMETER.getMessage(), "", ast.position);
             }
         }
         
         return argType;
     }
 
     @Override
     public Object visitArrayExpr(ArrayExpr ast, Object o) {
         Type t1 = (Type) ast.V.visit(this, o);
         Type t2 = (Type) ast.E.visit(this, o);
         
         if (!t1.isArrayType()) {
             reporter.reportError(ErrorMessage.SCALAR_FUNCTION_AS_ARRAY.getMessage(), "", ast.position);
             ast.type = StdEnvironment.errorType;
             return ast.type;
         }
         
         if (!t2.equals(StdEnvironment.intType)) {
             reporter.reportError(ErrorMessage.ARRAY_SUBSCRIPT_NOT_INTEGER.getMessage(), "", ast.position);
             ast.type = StdEnvironment.errorType;
             return ast.type;
         }
         
         ArrayType arrayType = (ArrayType) t1;
         ast.type = arrayType.T;
         
         return ast.type;
     }
     
     @Override
     public Object visitArrayInitExpr(ArrayInitExpr ast, Object o) {
         Type elementType = (Type) ast.IL.visit(this, o);
         ast.type = new ArrayType(elementType, new EmptyExpr(dummyPos), dummyPos);
         
         return ast.type;
     }
     
     @Override
     public Object visitArrayExprList(ArrayExprList ast, Object o) {
         Type currentElementType = (Type) ast.E.visit(this, null);
         if (currentElementType instanceof ErrorType) {
             return StdEnvironment.errorType;
         }
         
         if (o instanceof Object[]) {
             return checkArraySizeAndType(ast, (Object[])o, currentElementType);
         } else if (o instanceof Decl) {
             checkElementType(ast, ((Decl)o).T, currentElementType);
         }
         
         if (!(ast.EL instanceof EmptyArrayExprList)) {
             ast.EL.visit(this, o);
         }
         
         return currentElementType;
     }
     
     private Type checkArraySizeAndType(ArrayExprList ast, Object[] params, Type currentElementType) {
         Decl decl = (Decl)params[0];
         int declaredSize = (int)params[1];
         ArrayType arrayType = (ArrayType) decl.T;
         
         int currentIndex = countElementIndex(ast);
         
         if (currentIndex >= declaredSize) {
             reporter.reportError(ErrorMessage.EXCESS_ELEMENTS_IN_ARRAY_INITIALISER.getMessage(), 
                             "", ast.E.position);
         } else {
             checkElementType(ast, arrayType, currentElementType);
         }
         
         if (!(ast.EL instanceof EmptyArrayExprList)) {
             ast.EL.visit(this, params);
         }
         
         return arrayType.T;
     }
     
     private void checkElementType(ArrayExprList ast, Type arrayType, Type currentElementType) {
         if (arrayType instanceof ArrayType) {
             Type elementType = ((ArrayType) arrayType).T;
             
             if (!elementType.equals(currentElementType)) {
                 if (elementType.equals(StdEnvironment.floatType) && currentElementType.equals(StdEnvironment.intType)) {
                     ast.E = createI2f(ast.E);
                 } else {
                     reporter.reportError(ErrorMessage.WRONG_TYPE_FOR_ARRAY_INITIALISER.getMessage(), 
                                     "", ast.E.position);
                 }
             }
         }
     }
     
     private int countElementIndex(ArrayExprList ast) {
         int count = 0;
         AST current = ast;
         
         while (current != null && current.parent != null) {
             if (current.parent instanceof ArrayExprList) {
                 ArrayExprList parent = (ArrayExprList) current.parent;
                 if (parent.EL == current) {
                     count++;
                     current = parent;
                 } else {
                     break;
                 }
             } else {
                 break;
             }
         }
         
         return count;
     }
     
     @Override
     public Object visitEmptyArrayExprList(EmptyArrayExprList ast, Object o) {
         return StdEnvironment.errorType; 
     }
 
     // Declarations
 
     @Override
     public Object visitFuncDecl(FuncDecl ast, Object o) {
         declareVariable(ast.I, ast);
         idTable.openScope();
         curFuncReturn = ast.T.isVoidType();

         if (ast.I.spelling.equals("main")) {
            if (!ast.T.equals(StdEnvironment.intType)) {
                reporter.reportError(ErrorMessage.MAIN_RETURN_TYPE_NOT_INT.getMessage(), "", ast.position);
            }
        }
         ast.PL.visit(this, ast);
         
         ast.S.visit(this, ast);
         
         if (!curFuncReturn) {
             reporter.reportError(ErrorMessage.MISSING_RETURN_STATEMENT.getMessage(), "", ast.position);
         }
         
         idTable.closeScope();
         
         return null;
     }
 
     @Override
     public Object visitDeclList(DeclList ast, Object o) {
         ast.D.visit(this, null);
         ast.DL.visit(this, null);
         return null;
     }
 
     @Override
     public Object visitEmptyDeclList(EmptyDeclList ast, Object o) {
         return null;
     }
 
     @Override
     public Object visitGlobalVarDecl(GlobalVarDecl ast, Object o) {
         
         if (ast.T.equals(StdEnvironment.voidType)) {
             reporter.reportError(ErrorMessage.IDENTIFIER_DECLARED_VOID.getMessage(), ast.I.spelling, ast.position);
         }
         
         if (ast.T.isArrayType() && ((ArrayType)ast.T).T.equals(StdEnvironment.voidType)) {
             reporter.reportError(ErrorMessage.IDENTIFIER_DECLARED_VOID_ARRAY.getMessage(), ast.I.spelling, ast.position);
         }
         declareVariable(ast.I, ast);
         
         if(ast.E instanceof EmptyExpr){
             if(ast.T.isArrayType()){
                 ArrayType arrayType = (ArrayType) ast.T;
                 if (arrayType.E instanceof EmptyExpr) {
                     reporter.reportError(ErrorMessage.ARRAY_SIZE_MISSING.getMessage(), "", ast.position);
                 }
             }
         } else {
             if(ast.T.isArrayType()){
                 processArrayInit(ast);
             } else {
                 processScalarInit(ast);
             }
         }
         return ast.T;
     }
 
     @Override
     public Object visitLocalVarDecl(LocalVarDecl ast, Object o) {
         if (ast.T.equals(StdEnvironment.voidType)) {
             reporter.reportError(ErrorMessage.IDENTIFIER_DECLARED_VOID.getMessage(), ast.I.spelling, ast.position);
         }
         
         if (ast.T.isArrayType() && ((ArrayType)ast.T).T.equals(StdEnvironment.voidType)) {
             reporter.reportError(ErrorMessage.IDENTIFIER_DECLARED_VOID_ARRAY.getMessage(), ast.I.spelling, ast.position);
         }
         declareVariable(ast.I, ast);
         
         if(ast.E instanceof EmptyExpr){
             if(ast.T.isArrayType()){
                 ArrayType arrayType = (ArrayType) ast.T;
                 if (arrayType.E instanceof EmptyExpr) {
                     reporter.reportError(ErrorMessage.ARRAY_SIZE_MISSING.getMessage(), "", ast.position);
                 }
             }
         } else {
             if(ast.T.isArrayType()){
                 processArrayInit(ast);
             } else {
                 processScalarInit(ast);
             }
         }
         return ast.T;
     }
 
     private void processArrayInit(Decl ast) {
         if (ast instanceof GlobalVarDecl) {
             GlobalVarDecl gAst = (GlobalVarDecl) ast;
             ArrayType arrayType = (ArrayType) gAst.T;
             if (arrayType.T.isVoidType()) {
                 reporter.reportError(ErrorMessage.IDENTIFIER_DECLARED_VOID_ARRAY.getMessage(), 
                                 gAst.I.spelling, gAst.I.position);
             }
             if(gAst.E instanceof ArrayInitExpr){
                 if (arrayType.E instanceof EmptyExpr) {
                     int size = countArraynums(gAst.E);
                     arrayType.E = new IntExpr(new IntLiteral(Integer.toString(size), dummyPos), dummyPos);
                 }
                 else if (arrayType.E instanceof IntExpr) {
                     IntExpr sizeExpr = (IntExpr) arrayType.E;
                     int declaredSize = Integer.parseInt(((IntLiteral)sizeExpr.IL).spelling);
                     int actualSize = countArraynums(gAst.E);
                     
                     if (actualSize > declaredSize) {
                         gAst.E.visit(this, new Object[]{gAst, declaredSize});
                     } else {
                         gAst.E.visit(this, gAst);
                     }
                     return;
                 }
                 gAst.E.visit(this, gAst);
             } else {
                 reporter.reportError(ErrorMessage.INVALID_INITIALISER_SCALAR_FOR_ARRAY.getMessage(), 
                                 "", gAst.position);
             }
         } else if(ast instanceof LocalVarDecl){
             LocalVarDecl lAst = (LocalVarDecl) ast;
             ArrayType arrayType = (ArrayType) lAst.T;
             if (arrayType.T.isVoidType()) {
                 reporter.reportError(ErrorMessage.IDENTIFIER_DECLARED_VOID_ARRAY.getMessage(), 
                                 lAst.I.spelling, lAst.position);
             }
             
             if (lAst.E instanceof ArrayInitExpr) {
                 if (arrayType.E instanceof EmptyExpr) {
                     int size = countArraynums(lAst.E);
                     arrayType.E = new IntExpr(new IntLiteral(Integer.toString(size), dummyPos), dummyPos);
                 }
                 else if (arrayType.E instanceof IntExpr) {
                     IntExpr sizeExpr = (IntExpr) arrayType.E;
                     int declaredSize = Integer.parseInt(((IntLiteral)sizeExpr.IL).spelling);
                     int actualSize = countArraynums(lAst.E);
                     
                     if (actualSize > declaredSize) {
                         lAst.E.visit(this, new Object[]{lAst, declaredSize});
                     } else {
                         lAst.E.visit(this, lAst);
                     }
                     return;
                 }
                 lAst.E.visit(this, lAst);
             } else {
                 reporter.reportError(ErrorMessage.INVALID_INITIALISER_SCALAR_FOR_ARRAY.getMessage(), 
                                 "", lAst.position);
             }   
         }
     }
 
     private void processScalarInit(Decl ast) {
         if(ast instanceof GlobalVarDecl){
             GlobalVarDecl gAst = (GlobalVarDecl) ast;
             if (gAst.E instanceof ArrayInitExpr) {
                 reporter.reportError(ErrorMessage.INVALID_INITIALISER_ARRAY_FOR_SCALAR.getMessage(), 
                                     "", gAst.position);
             } else {
                 Type t2 = (Type) gAst.E.visit(this, null);
                 if(gAst.T.assignable(t2)){
                     if (gAst.T.equals(StdEnvironment.floatType) && t2.equals(StdEnvironment.intType)) {
                         gAst.E = createI2f(gAst.E);
                     }
                 } else {
                     reporter.reportError(ErrorMessage.INCOMPATIBLE_TYPE_FOR_ASSIGNMENT.getMessage(), "", gAst.position);
                 }
             } 
         } else if(ast instanceof LocalVarDecl){
             LocalVarDecl lAst = (LocalVarDecl) ast;
             if (lAst.E instanceof ArrayInitExpr) {
                 reporter.reportError(ErrorMessage.INVALID_INITIALISER_ARRAY_FOR_SCALAR.getMessage(), 
                                     "", lAst.position);
             } else {
                 Type t2 = (Type) lAst.E.visit(this, null);
                 if (lAst.T.assignable(t2)) {
                     if (lAst.T.equals(StdEnvironment.floatType) && t2.equals(StdEnvironment.intType)) {
                         lAst.E = createI2f(lAst.E);
                     }
                 } else {
                     reporter.reportError(ErrorMessage.INCOMPATIBLE_TYPE_FOR_ASSIGNMENT.getMessage(), "", lAst.position);
                 }
             }  
         }
     }
 
     private int countArraynums(Expr initExpr) {
         if (!(initExpr instanceof ArrayInitExpr)) {
             return 0;
         }
         
         ArrayInitExpr arrayInitExpr = (ArrayInitExpr) initExpr;
         AST currentList = arrayInitExpr.IL;
         int count = 0;
         while (currentList instanceof ArrayExprList) {
             count++;
             
             ArrayExprList exprList = (ArrayExprList) currentList;
             currentList = exprList.EL;
         }
         
         return count;
     }
     
     // Parameters
 
     @Override
     public Object visitParaList(ParaList ast, Object o) {
        ast.P.visit(this, o);
        ast.PL.visit(this, o);
        return null;
     }
 
     @Override
     public Object visitParaDecl(ParaDecl ast, Object o) {
        declareVariable(ast.I, ast);

         if (ast.T.isVoidType()) {
             reporter.reportError(ErrorMessage.IDENTIFIER_DECLARED_VOID.getMessage(), ast.I.spelling, ast.I.position);
         } else if (ast.T.isArrayType()) {
             if (((ArrayType) ast.T).T.isVoidType()) {
                 reporter.reportError(ErrorMessage.IDENTIFIER_DECLARED_VOID_ARRAY.getMessage(), ast.I.spelling, ast.I.position);
             }
         }
         return null;
     }
 
     @Override
     public Object visitEmptyParaList(EmptyParaList ast, Object o) {
         return null;
     }
 
     // Types
 
     @Override
     public Object visitErrorType(ErrorType ast, Object o) {
         return StdEnvironment.errorType;
     }
 
     @Override
     public Object visitBooleanType(BooleanType ast, Object o) {
         return StdEnvironment.booleanType;
     }
 
     @Override
     public Object visitIntType(IntType ast, Object o) {
         return StdEnvironment.intType;
     }
 
     @Override
     public Object visitFloatType(FloatType ast, Object o) {
         return StdEnvironment.floatType;
     }
 
     @Override
     public Object visitStringType(StringType ast, Object o) {
         return StdEnvironment.stringType;
     }
 
     @Override
     public Object visitVoidType(VoidType ast, Object o) {
         return StdEnvironment.voidType;
     }
 
     @Override
     public Object visitArrayType(ArrayType ast, Object o) {
         return ast;
     }
 
     // Literals, Identifiers and Operators
 
     @Override
     public Object visitIdent(Ident I, Object o) {
        Optional<IdEntry> binding = idTable.retrieve(I.spelling);
        binding.ifPresent(entry -> I.decl = entry.attr); // Link the identifier to its declaration
        return binding.map(entry -> entry.attr).orElse(null);
     }
 
     @Override
     public Object visitBooleanLiteral(BooleanLiteral SL, Object o) {
         return StdEnvironment.booleanType;
     }
 
     @Override
     public Object visitIntLiteral(IntLiteral IL, Object o) {
         return StdEnvironment.intType;
     }
 
     @Override
     public Object visitFloatLiteral(FloatLiteral IL, Object o) {
         return StdEnvironment.floatType;
     }
 
     @Override
     public Object visitStringLiteral(StringLiteral IL, Object o) {
         return StdEnvironment.stringType;
     }
 
     @Override
     public Object visitOperator(Operator O, Object o) {
         return null;
     }
 
     // variable names
 
     // Creates a small AST to represent the "declaration" of each built-in
     // function, and enters it in the symbol table.
 
     private FuncDecl declareStdFunc(Type resultType, String id, VC.ASTs.List pl) {
         var binding = new FuncDecl(resultType, new Ident(id, dummyPos), pl,
                 new EmptyStmt(dummyPos), dummyPos);
         idTable.insert(id, binding);
         return binding;
     }
 
     // Creates small ASTs to represent "declarations" of all
     // build-in functions.
     // Inserts these "declarations" into the symbol table.
 
     private final static Ident dummyI = new Ident("x", dummyPos);
 
     private void establishStdEnvironment() {
         // Define four primitive types
         // errorType is assigned to ill-typed expressions
 
         StdEnvironment.booleanType = new BooleanType(dummyPos);
         StdEnvironment.intType = new IntType(dummyPos);
         StdEnvironment.floatType = new FloatType(dummyPos);
         StdEnvironment.stringType = new StringType(dummyPos);
         StdEnvironment.voidType = new VoidType(dummyPos);
         StdEnvironment.errorType = new ErrorType(dummyPos);
 
         // enter into the declarations for built-in functions into the table
 
         StdEnvironment.getIntDecl = declareStdFunc(StdEnvironment.intType,
                 "getInt", new EmptyParaList(dummyPos));
         StdEnvironment.putIntDecl = declareStdFunc(StdEnvironment.voidType,
                 "putInt", new ParaList(
                         new ParaDecl(StdEnvironment.intType, dummyI, dummyPos),
                         new EmptyParaList(dummyPos), dummyPos));
         StdEnvironment.putIntLnDecl = declareStdFunc(StdEnvironment.voidType,
                 "putIntLn", new ParaList(
                         new ParaDecl(StdEnvironment.intType, dummyI, dummyPos),
                         new EmptyParaList(dummyPos), dummyPos));
         StdEnvironment.getFloatDecl = declareStdFunc(StdEnvironment.floatType,
                 "getFloat", new EmptyParaList(dummyPos));
         StdEnvironment.putFloatDecl = declareStdFunc(StdEnvironment.voidType,
                 "putFloat", new ParaList(
                         new ParaDecl(StdEnvironment.floatType, dummyI, dummyPos),
                         new EmptyParaList(dummyPos), dummyPos));
         StdEnvironment.putFloatLnDecl = declareStdFunc(StdEnvironment.voidType,
                 "putFloatLn", new ParaList(
                         new ParaDecl(StdEnvironment.floatType, dummyI, dummyPos),
                         new EmptyParaList(dummyPos), dummyPos));
         StdEnvironment.putBoolDecl = declareStdFunc(StdEnvironment.voidType,
                 "putBool", new ParaList(
                         new ParaDecl(StdEnvironment.booleanType, dummyI, dummyPos),
                         new EmptyParaList(dummyPos), dummyPos));
         StdEnvironment.putBoolLnDecl = declareStdFunc(StdEnvironment.voidType,
                 "putBoolLn", new ParaList(
                         new ParaDecl(StdEnvironment.booleanType, dummyI, dummyPos),
                         new EmptyParaList(dummyPos), dummyPos));
 
         StdEnvironment.putStringLnDecl = declareStdFunc(StdEnvironment.voidType,
                 "putStringLn", new ParaList(
                         new ParaDecl(StdEnvironment.stringType, dummyI, dummyPos),
                         new EmptyParaList(dummyPos), dummyPos));
 
         StdEnvironment.putStringDecl = declareStdFunc(StdEnvironment.voidType,
                 "putString", new ParaList(
                         new ParaDecl(StdEnvironment.stringType, dummyI, dummyPos),
                         new EmptyParaList(dummyPos), dummyPos));
 
         StdEnvironment.putLnDecl = declareStdFunc(StdEnvironment.voidType,
                 "putLn", new EmptyParaList(dummyPos));
     }
 
 
     
     // VALUE
     @Override
     public Object visitSimpleVar(SimpleVar ast, Object o) {
         ast.I.visit(this, o);
         AST dAST = ((Ident) ast.I).decl;
         
         Type resultType;
         
         if (dAST == null) {
             reporter.reportError(ErrorMessage.IDENTIFIER_UNDECLARED.getMessage(), ((Ident)ast.I).spelling, ast.position);
             resultType = StdEnvironment.errorType;
         } else if (dAST instanceof Decl) {
             Decl decl = (Decl) dAST;
             if (decl instanceof FuncDecl) {
                 if (!(o instanceof CallExpr) && !(o instanceof ExprStmt)) {
                     reporter.reportError(ErrorMessage.ARRAY_FUNCTION_AS_SCALAR.getMessage(), "", ast.position);
                     resultType = StdEnvironment.errorType;
                 } else {
                     resultType = decl.T;
                 }
             } else {
                 resultType = decl.T;
                 
                 if (resultType.isArrayType() && o instanceof ExprStmt) {
                     reporter.reportError(ErrorMessage.ARRAY_FUNCTION_AS_SCALAR.getMessage(), "", ast.position);
                 }
             }
         } else {
             resultType = StdEnvironment.errorType;
         }
         
         ast.type = resultType;
         return resultType;
     }
 }
 
 