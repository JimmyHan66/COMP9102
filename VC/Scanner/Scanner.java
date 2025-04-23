/*
 * Scanner.java
 *
 * Sun 09 Feb 2025 13:31:52 AEDT
 *
 * The starter code here is provided as a high-level guide for implementation.
 *
 * You may completely disregard the starter code and develop your own solution,
 * provided that it maintains the same public interface.
 *
 */

package VC.Scanner;

import VC.ErrorReporter;

public final class Scanner {

    private SourceFile sourceFile;
    private ErrorReporter errorReporter;
    private boolean debug;

    private StringBuilder currentSpelling;
    private char currentChar;
    private SourcePosition sourcePos;

    private int currentTokenColumnStart;
    private int currentTokenLine;
    private int currentTokenColumnEnd;
    // =========================================================

    public Scanner(SourceFile source, ErrorReporter reporter) {
        sourceFile = source;
        errorReporter = reporter;
        debug = false;

        // Initiaise currentChar for the starter code.
        // Change it if necessary for your full implementation
        currentChar = getNextChar();

        // Initialise your counters for counting line and column numbers here
        currentTokenColumnEnd = 1;
        currentTokenColumnStart = 1;
        currentTokenLine = 1;
    }

    public void enableDebugging() {
        debug = true;
    }

    // accept gets the next character from the source program.
    private void accept() {
  	// You may save the lexeme of the current token incrementally here
  	// You may also increment your line and column counters here
        currentSpelling.append(currentChar);
        currentChar = getNextChar();
        currentTokenColumnEnd++;
    }


    // inspectChar returns the n-th character after currentChar in the input stream.
    // If there are fewer than nthChar characters between currentChar
    // and the end of file marker, SourceFile.eof is returned.
    //
    // Both currentChar and the current position in the input stream
    // are *not* changed. Therefore, a subsequent call to accept()
    // will always return the next char after currentChar.

    // That is, inspectChar does not change

    private char inspectChar(int nthChar) {
        return sourceFile.inspectChar(nthChar);
    }

    // Wrap the sourceFile method to get the next character
    private char getNextChar() {
        return sourceFile.getNextChar();
    }

    private int nextToken() {
        // Tokens: separators, operators, literals, identifiers, and keywords
        switch (currentChar) {
            // separators
            case '(':
                accept();
                return Token.LPAREN;
            case ')':
                accept();
                return Token.RPAREN;
            case '{':
                accept();
                return Token.LCURLY;
            case '}':
                accept();
                return Token.RCURLY;
            case '[':
                accept();
                return Token.LBRACKET;
            case ']':
                accept();
                return Token.RBRACKET;
            case ';':
                accept();
                return Token.SEMICOLON;
            case ',':
                accept();
                return Token.COMMA;

            // operators
            case '+':
                accept();
                return Token.PLUS;
            case '-':
                accept();
                return Token.MINUS;
            case '*':
                accept();
                return Token.MULT;
            case '/':
                accept();
                return Token.DIV;
            case '!':
                accept();
                if (currentChar == '=') {
                    accept();
                    return Token.NOTEQ;
                }
                return Token.NOT;
            case '=':
                accept();
                if (currentChar == '=') {
                    accept();
                    return Token.EQEQ;
                }
                return Token.EQ;
            case '<':
                accept();
                if (currentChar == '=') {
                    accept();
                    return Token.LTEQ;
                }
                return Token.LT;
            case '>':
                accept();
                if (currentChar == '=') {
                    accept();
                    return Token.GTEQ;
                }
                return Token.GT;
            case '&':
                accept();
                if (currentChar == '&') {
                    accept();
                    return Token.ANDAND;
                }
                return Token.ERROR;
            case '|':
                accept();
                if (currentChar == '|') {
                    accept();
                    return Token.OROR;
                }
                return Token.ERROR;
            case '.':
                return handle_floats();
            case '"':
                return handle_strings();
            case '0': case '1': case '2': case '3': case '4':
            case '5': case '6': case '7': case '8': case '9':
                return handle_numbers();
	        // ...
            case SourceFile.eof:
                currentSpelling.append(Token.spell(Token.EOF));
                currentTokenColumnEnd = currentTokenColumnEnd + 1;
                return Token.EOF;
            default:
                // Identifiers and reserved words
                if(Character.isLetter(currentChar) || currentChar == '_') {
                    return handle_identifiers_reserved();
                } else{
                    break;
                }
        }
        // ...
        accept();
        return Token.ERROR;
    }

    private int handle_strings() {
        currentChar = getNextChar();
        currentTokenColumnEnd = currentTokenColumnEnd + 1;
        boolean stringTerminated = false;
        while(!stringTerminated) {
            if(currentChar == '\\') {
                currentChar = getNextChar();
                currentTokenColumnEnd = currentTokenColumnEnd + 1;
                if( currentChar == 'b' || currentChar == 'f' || currentChar == 'n' ||
                    currentChar == 't' || currentChar == 'r' || currentChar == '"' ||
                    currentChar == '\'' || currentChar == '\\') {
                        char temp;
                        switch(currentChar) {
                            case 'b':
                                temp = '\b';
                                break;
                            case 'f':
                                temp = '\f';
                                break;
                            case 'n':
                                temp = '\n';
                                break;
                            case 't':
                                temp = '\t';
                                break;
                            case 'r':
                                temp = '\r';
                                break;
                            case '\"':
                                temp = '\"';
                                break;
                            case '\'':
                                temp = '\'';
                                break;
                            case '\\':
                                temp = '\\';
                                break;
                            default:
                                temp = currentChar;
                        }
                        currentSpelling.append(temp);
                        currentChar = getNextChar();
                        currentTokenColumnEnd = currentTokenColumnEnd + 1;
                } else {
                    if(currentChar == '\n' || currentChar == SourceFile.eof) {
                        SourcePosition err_pos1 = new SourcePosition(currentTokenLine, currentTokenLine, currentTokenColumnStart, currentTokenColumnEnd-1);
                        errorReporter.reportError( "\\" + currentChar +": illegal escape character", "", err_pos1);
                        currentSpelling.append('\\');
                        SourcePosition err_pos2 = new SourcePosition(currentTokenLine, currentTokenLine, currentTokenColumnStart, currentTokenColumnStart);
                        errorReporter.reportError(currentSpelling+": unterminated string", "", err_pos2);
                        stringTerminated = true;
                        break;
                    }
                    SourcePosition err_pos = new SourcePosition();
                    err_pos.lineStart = currentTokenLine;
                    err_pos.lineFinish = currentTokenLine;
                    err_pos.charStart = currentTokenColumnStart;
                    err_pos.charFinish = currentTokenColumnEnd - 1;
                    errorReporter.reportError( "\\" + currentChar +": illegal escape character", "", err_pos);
                    currentSpelling.append('\\');
                    accept();
                }
            } else if(currentChar == '"') {
                currentChar = getNextChar();
                currentTokenColumnEnd = currentTokenColumnEnd + 1;
                stringTerminated = true;
            } else if(currentChar == '\n' || currentChar == SourceFile.eof) {
                SourcePosition err_pos = new SourcePosition();
                err_pos.lineStart = currentTokenLine;
                err_pos.lineFinish = currentTokenLine;
                err_pos.charStart = currentTokenColumnStart;
                err_pos.charFinish = currentTokenColumnStart;
                errorReporter.reportError(currentSpelling+": unterminated string", "", err_pos);
                stringTerminated = true;
            } else {
                accept();
            }
        }
        return Token.STRINGLITERAL;
    }

    private int handle_floats() {
        accept();
        if(!Character.isDigit(currentChar)) {
            return Token.ERROR;
        }
        boolean floatTerminated = false;
        while(!floatTerminated) {
            while(Character.isDigit(currentChar)) {
                accept();
            }
            char nextChar = inspectChar(1);
            if (currentChar == 'e' || currentChar == 'E') {
                if (Character.isDigit(nextChar) ||((nextChar == '+' || nextChar == '-') && Character.isDigit(inspectChar(2)))) {
                    accept();
                    if (currentChar == '+' || currentChar == '-') {
                        accept();
                    }
                    while(Character.isDigit(currentChar)) {
                        accept();
                    }
                }
            }
            floatTerminated = true;
        }
        return Token.FLOATLITERAL;
    }

    private int handle_numbers() {
        while(Character.isDigit(currentChar)) {
            accept();
        }
        if(currentChar == '.') {
            accept();
            boolean floatTerminated = false;
            while(!floatTerminated) {
                while(Character.isDigit(currentChar)) {
                    accept();
                }
                char nextChar = inspectChar(1);
                if (currentChar == 'e' || currentChar == 'E') {
                    if (Character.isDigit(nextChar) ||((nextChar == '+' || nextChar == '-') && Character.isDigit(inspectChar(2)))) {
                        accept();
                        if (currentChar == '+' || currentChar == '-') {
                            accept();
                        }
                        while(Character.isDigit(currentChar)) {
                            accept();
                        }
                    }
                }
                floatTerminated = true;
            }
            return Token.FLOATLITERAL;
        } else if(currentChar == 'e' || currentChar == 'E') {
            char nextChar = inspectChar(1);
            if (Character.isDigit(nextChar) ||((nextChar == '+' || nextChar == '-') && Character.isDigit(inspectChar(2)))) {
                accept();
                if (currentChar == '+' || currentChar == '-') {
                    accept();
                }
                while(Character.isDigit(currentChar)) {
                    accept();
                }
                return Token.FLOATLITERAL;
            }
        }

        return Token.INTLITERAL;
    }

    private int handle_identifiers_reserved() {
        while (Character.isLetterOrDigit(currentChar) || currentChar == '_') {
            accept();
        }
        if(currentSpelling.toString().equals("true") || currentSpelling.toString().equals("false")) {
            return Token.BOOLEANLITERAL;
        }
        return Token.ID;
    }

    private void skipSpaceAndComments() {
        boolean done = false;
        while (!done) {
            // Handle whitespace characters
            if (currentChar == ' ') {
                currentChar = getNextChar();
                currentTokenColumnEnd++;
                currentTokenColumnStart = currentTokenColumnEnd;
            } else if (currentChar == '\t') {
                currentChar = getNextChar();
                currentTokenColumnEnd = ((currentTokenColumnEnd - 1) / 8 + 1) * 8 + 1;
            } else if (currentChar == '\n') {
                currentChar = getNextChar();
                currentTokenLine++;
                currentTokenColumnEnd = 1;
                currentTokenColumnStart = 1;
            }
            // Handle comments
            else if (currentChar == '/') {
                if (inspectChar(1) == '/') {
                    // Skip everything until end of line
                    while (currentChar != '\n' && currentChar != SourceFile.eof) {
                        currentChar = getNextChar();
                        currentTokenColumnEnd++;
                    }
                    // Handle the newline if we found one
                    if (currentChar == '\n') {
                        currentChar = getNextChar();
                        currentTokenLine++;
                        currentTokenColumnEnd = 1;
                    }
                } else if (inspectChar(1) == '*') {
                    // Create positions for error reporting
                    SourcePosition commentPos = new SourcePosition();
                    commentPos.lineStart = currentTokenLine;
                    commentPos.charStart = currentTokenColumnEnd;
                    currentChar = getNextChar();
                    currentTokenColumnEnd++;
                    currentChar = getNextChar();
                    currentTokenColumnEnd++;
                    boolean commentTerminated = false;
                    while (!commentTerminated && currentChar != SourceFile.eof) {
                        if (currentChar == '*' && inspectChar(1) == '/') {
                            currentChar = getNextChar();
                            currentTokenColumnEnd++;
                            currentChar = getNextChar();
                            currentTokenColumnEnd++;
                            commentTerminated = true;
                        } else {
                            if (currentChar == '\n') {
                                currentChar = getNextChar();
                                currentTokenLine++;
                                currentTokenColumnEnd = 1;
                                currentTokenColumnStart = 1;
                            } else if (currentChar == '\t') {
                                currentChar = getNextChar();
                                currentTokenColumnEnd = ((currentTokenColumnEnd - 1) / 8 + 1) * 8 + 1;
                            } else {
                                currentChar = getNextChar();
                                currentTokenColumnEnd++;
                            }
                        }
                    }
                    // Check if comment was closed
                    if (!commentTerminated) {
                        // Report unterminated comment error
                        commentPos.lineFinish = commentPos.lineStart;
                        commentPos.charFinish = commentPos.charStart;
                        errorReporter.reportError(": unterminated comment", "", commentPos);
                    }
                } else {
                    done = true;
                }
            } else {
                done = true;
            }
        }
        currentTokenColumnStart = currentTokenColumnEnd;
    }

    public Token getToken() {
        Token token;
        int kind;

        // Skip white space and comments
        skipSpaceAndComments();
        currentSpelling = new StringBuilder();

        sourcePos = new SourcePosition();

        // You need to record the position of the current token somehow

        kind = nextToken();
        sourcePos.lineStart = currentTokenLine;
        sourcePos.lineFinish = currentTokenLine;
        sourcePos.charStart = currentTokenColumnStart;
        sourcePos.charFinish = currentTokenColumnEnd - 1;
        token = new Token(kind, currentSpelling.toString(), sourcePos);
        currentTokenColumnStart = currentTokenColumnEnd;
   	// * do not remove these three lines below (for debugging purposes)
        if (debug) {
            System.out.println(token);
        }
        return token;
    }
}
