grammar Javamm;

@header {
    package pt.up.fe.comp2024;
}

MUL : '*' ;
ADD : '+' ;
SUB : '-';
DIV : '/' ;
AND : '&&' ;
LESS : '<' ;
NEG : '!' ;

RETURN : 'return' ;
CLASS : 'class' ;
INT : 'int' ;
PUBLIC : 'public' ;
NEW : 'new' ;
IMPORT : 'import' ;
EXTENDS : 'extends' ;
STATIC : 'static' ;
VOID : 'void' ;
THIS : 'this' ;
TRUE : 'true';
FALSE : 'false';
BOOLEAN : 'boolean' ;
INTEGER : [0] | ([1-9][0-9]*);
ID : [a-zA-Z_$][a-zA-Z_$0-9]* ;

SINGLE_COMMENT : '//' .*? '\n' -> skip ;
MULTI_COMMENT : '/*' .*? '*/' -> skip ;
WS : [\t\n\r\f ]+ -> skip ;

program
    : importDecl* classD=classDecl EOF
    ;

importDecl
    : IMPORT name+=ID ('.' name+=ID)* ';'
    ;

classDecl
    : CLASS name=ID
        (EXTENDS superClass=ID)?
        '{'
            varDecl*
            methodDecl*
        '}'
    ;

varDecl
    : type name=ID ';'
    ;

type locals[boolean isArray=false]
    : name=INT '[' ']' {$isArray=true;} #ArrayType
    | name=INT '...' {$isArray=true;} #VarargType
    | name=ID '[' ']' {$isArray=true;} #ObjectArrayType
    | name=INT #IntType
    | name=BOOLEAN #BoolType
    | name=VOID #VoidType
    | name=ID #ObjectType
    ;

methodDecl locals[boolean isPublic=false, boolean isStatic=false]
    : (PUBLIC {$isPublic=true;})? (STATIC {$isStatic=true;})?
        type name=ID
        '(' (param (',' param)*)? ')'
        '{'
            varDecl*
            stmt*
        '}'
    ;

param
    : type name=ID
    ;


stmt
    : '{' stmt* '}' #Stmts
    | name=ID '=' expr ';' #AssignStmt
    | RETURN expr ';' #ReturnStmt
    | 'if' '(' condition=expr ')' stmt 'else' stmt #IfStmt
    | 'while' '(' condition=expr ')' stmt #WhileStmt
    | expr ';' #ExprStmt
    | name=ID '[' index=expr ']' '=' array=expr ';' #ArrayAssignStmt
    ;

expr
    : '(' expr ')' #ParensExpr
    | expr '.' len=ID #LenExpr
    | array=expr '[' index=expr ']' #ArrayElemExpr
    | '[' (expr (',' expr)*)? ']' #ArrayExpr
    | object=expr '.' method=ID '(' (expr (',' expr)*)? ')' #MethodExpr
    | NEW INT '[' index=expr ']' #NewArrayExpr
    | NEW name=ID '(' ')' #NewObjectExpr
    | op=NEG expr #NegExpr
    | expr op=(MUL | DIV) expr #BinaryExpr
    | expr op=(ADD | SUB) expr #BinaryExpr
    | expr op=LESS expr #BinaryExpr
    | expr op=AND expr #BinaryExpr
    | value=INTEGER #IntegerLiteral
    | value=(TRUE | FALSE) #BooleanLiteral
    | name=THIS #ThisExpr
    | name=ID #VarRefExpr
    ;



