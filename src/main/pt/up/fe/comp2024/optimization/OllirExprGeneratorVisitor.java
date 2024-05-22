package pt.up.fe.comp2024.optimization;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.AJmmVisitor;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.ollir.OllirUtils;
import pt.up.fe.comp2024.ast.Kind;
import pt.up.fe.comp2024.ast.TypeUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static pt.up.fe.comp2024.ast.Kind.*;

/**
 * Generates OLLIR code from JmmNodes that are expressions.
 */
public class OllirExprGeneratorVisitor extends AJmmVisitor<Void, OllirExprResult> {

    private static final String SPACE = " ";
    private static final String ASSIGN = ":=";
    private static final String NEW = "new";
    private static final String INVOKESPECIAL = "invokespecial";
    private static final String INVOKEVIRTUAL = "invokevirtual";
    private static final String INVOKESTATIC = "invokestatic";



    private final String END_STMT = ";\n";

    private final SymbolTable table;
    private String currMethod;
    public void setCurrMethod(String methodName) {
        this.currMethod = methodName;
    }

    public OllirExprGeneratorVisitor(SymbolTable table) {
        this.table = table;
    }

    @Override
    protected void buildVisitor() {
        addVisit(VAR_REF_EXPR, this::visitVarRef);
        addVisit(BINARY_EXPR, this::visitBinExpr);
        addVisit(INTEGER_LITERAL, this::visitInteger);
        addVisit(NEW_OBJECT_EXPR, this::visitNewObjExpr);
        addVisit(METHOD_EXPR,this::visitMethodExpr);
        addVisit(NEG_EXPR, this::visitNegExpr);
        addVisit(BOOLEAN_LITERAL, this::visitBoolean);
        addVisit(PARENS_EXPR, this::visitParensExpr);
        addVisit(THIS_EXPR, this::visitThisExpr);
        setDefaultVisit(this::defaultVisit);
    }



    private OllirExprResult visitInteger(JmmNode node, Void unused) {
        Type intType = new Type(TypeUtils.getIntTypeName(), false);
        String ollirIntType = OptUtils.toOllirType(intType);
        String code = node.get("value") + ollirIntType;
        return new OllirExprResult(code);
    }

    private OllirExprResult visitBoolean(JmmNode node, Void unused) {
        Type boolType = new Type(TypeUtils.getBoolTypeName(), false);
        String ollirBoolType = OptUtils.toOllirType(boolType);
        String code = OptUtils.toOllirBool(node.get("value")) + ollirBoolType;
        return new OllirExprResult(code);
    }


    private OllirExprResult visitBinExpr(JmmNode node, Void unused) {

        JmmNode left = node.getJmmChild(0);
        JmmNode right = node.getJmmChild(1);

        String operator = node.get("op");

        String ollirType = switch (operator) {
            case "+", "*", "-", "/", "<" -> ".i32";
            case "&&" -> ".bool";
            default -> "";
        };

        OllirExprResult lhs = visit(left);
        OllirExprResult rhs = visit(right);

        StringBuilder computation = new StringBuilder();

        // code to compute the children
        computation.append(lhs.getComputation());
        computation.append(rhs.getComputation());

        String leftCode = lhs.getCode();
        String rightCode = rhs.getCode();

        while (left.getKind().equals("ParensExpr")) left = left.getChild(0);
        while (right.getKind().equals("ParensExpr")) right = right.getChild(0);

        if (left.getKind().equals("MethodExpr")) {
            if (Objects.requireNonNull(TypeUtils.getExprType(left, table, currMethod)).hasAttribute("isExternal")) {
                leftCode = leftCode.substring(0, leftCode.lastIndexOf(".")) + ollirType + END_STMT;
            }
            String newTmp = OptUtils.getTemp() + ollirType;
            computation.append(newTmp)
                    .append(SPACE).append(ASSIGN).append(ollirType).append(SPACE)
                    .append(leftCode);
            leftCode = newTmp;
        }

        if (right.getKind().equals("MethodExpr")) {
            if (Objects.requireNonNull(TypeUtils.getExprType(right, table, currMethod)).hasAttribute("isExternal")) {
                rightCode = rightCode.substring(0, rightCode.lastIndexOf(".")) + ollirType + END_STMT;
            }
            String newTmp = OptUtils.getTemp() + ollirType;
            computation.append(newTmp)
                    .append(SPACE).append(ASSIGN).append(ollirType).append(SPACE)
                    .append(rightCode);
            rightCode = newTmp;
        }


        // code to compute self
        Type resType = TypeUtils.getExprType(node, table, currMethod);
        assert resType != null;
        String resOllirType = OptUtils.toOllirType(resType);
        String code = OptUtils.getTemp() + resOllirType;

        computation.append(code).append(SPACE)
                .append(ASSIGN).append(resOllirType).append(SPACE)
                .append(leftCode).append(SPACE);

        Type type = TypeUtils.getExprType(node, table, currMethod);
        assert type != null;
        computation.append(node.get("op")).append(OptUtils.toOllirType(type)).append(SPACE)
                .append(rightCode).append(END_STMT);

        return new OllirExprResult(code, computation);
    }


    private OllirExprResult visitVarRef(JmmNode node, Void unused) {
        StringBuilder computation = new StringBuilder();
        String code;

        String id = node.get("name");
        Type type = TypeUtils.getExprType(node, table, currMethod);
        assert type != null;
        String ollirType = OptUtils.toOllirType(type);


        boolean isField = table.getFields().stream().anyMatch(field -> field.getName().equals(id));
        boolean isLocal = table.getLocalVariables(currMethod).stream().anyMatch(local -> local.getName().equals(id));
        boolean isParam = table.getParameters(currMethod).stream().anyMatch(param -> param.getName().equals(id));

        if(isField && !(isLocal || isParam)){
            code = OptUtils.getTemp() + ollirType;
            computation.append(code)
                    .append(SPACE).append(ASSIGN).append(ollirType).append(SPACE)
                    .append("getfield(this, ").append(id).append(ollirType).append(")")
                    .append(ollirType).append(END_STMT);
        }else{
            code = id + ollirType;
        }


        return new OllirExprResult(code,computation);
    }

    private OllirExprResult visitNewObjExpr(JmmNode node, Void unused){
        StringBuilder computation = new StringBuilder();

        String resType = node.get("name");
        String resOllirType = "." + resType;
        String code = OptUtils.getTemp() + resOllirType;

        computation.append(code).append(SPACE)
                .append(ASSIGN).append(resOllirType).append(SPACE)
                .append(NEW+"(").append(resType).append(")").append(resOllirType)
                .append(END_STMT);

        computation.append(INVOKESPECIAL+"(")
                .append(code)
                .append(", \"<init>\").V")
                .append(END_STMT);

        return new OllirExprResult(code, computation);
    }

    private OllirExprResult visitMethodExpr(JmmNode node, Void unused){
        StringBuilder code = new StringBuilder();
        StringBuilder computation = new StringBuilder();
        String method = node.get("method");
        String ollirReturnType = "";

        JmmNode object = node.getObject("object", JmmNode.class);
        Type objectType = TypeUtils.getExprType(object, table, currMethod);
        String tipo = OptUtils.toOllirType(objectType);

        while (object.getKind().equals("ParensExpr")) object = object.getJmmChild(0);

        // recursive call
        if (object.getKind().equals("MethodExpr")) {
            OllirExprResult left = visit(object);
            computation.append(left.getComputation());

            String temp = OptUtils.getTemp() + tipo;
            computation.append(temp).append(SPACE)
                    .append(ASSIGN).append(tipo).append(SPACE)
                    .append(left.getCode());

            if (objectType.getName().equals(table.getClassName())) {
                Type returnType = table.getReturnType(method);
                ollirReturnType = OptUtils.toOllirType(returnType);
            }
            else {
                ollirReturnType = OptUtils.toOllirType(objectType);
            }

            code.append(INVOKEVIRTUAL).append("(").append(temp).append(", \"").append(method).append("\"");

            var arguments = node.getChildren();
            arguments.remove(0);
            OllirExprResult argumentsResult = buildArguments(arguments, method, objectType);

            computation.append(argumentsResult.getComputation());
            code.append(argumentsResult.getCode()).append(")").append(ollirReturnType).append(END_STMT);
            return new OllirExprResult(code.toString(), computation.toString());
        }

        else if (object.getKind().equals("ThisExpr") || object.getKind().equals("NewObjectExpr")) {
            OllirExprResult expr = visit(object);
            String className = TypeUtils.getExprType(object, table, currMethod).getName();
            computation.append(expr.getComputation());
            Type returnType = table.getReturnType(method);
            if (className.equals(table.getClassName()) && returnType != null) ollirReturnType = OptUtils.toOllirType(returnType);
            else ollirReturnType = "." + object.get("name");

            code.append(INVOKEVIRTUAL).append("(").append(expr.getCode());
        }

        else if (object.getKind().equals("VarRefExpr")) {

            OllirExprResult objectResult = visit(object);

            // static imported class
            if (objectType.hasAttribute("isExternal") && !objectType.hasAttribute("isInstance")) {
                ollirReturnType = ".V";
                code.append(INVOKESTATIC).append("(").append(objectType.getName());
            }

            else {
                // current class object
                if (objectType.getName().equals(table.getClassName())) {
                    ollirReturnType = OptUtils.toOllirType(table.getReturnType(method));
                }

                // external object
                else {
                    ollirReturnType = ".V";
                }

                code.append(INVOKEVIRTUAL).append("(").append(objectResult.getCode());
                }
            }

        var arguments = node.getChildren();
        arguments.remove(0);
        OllirExprResult argumentResult = buildArguments(arguments, method, objectType);
        computation.append(argumentResult.getComputation());

        code.append(", \"").append(method).append("\"")
            .append(argumentResult.getCode())
            .append(")").append(ollirReturnType)
            .append(END_STMT);

        return new OllirExprResult(code.toString(), computation);
    }

    private OllirExprResult buildArguments(List<JmmNode> arguments, String method, Type objectType) {
        StringBuilder argCode = new StringBuilder();
        StringBuilder computation = new StringBuilder();

        for (int i = 0; i < arguments.size(); i++) {
            JmmNode argument = arguments.get(i);
            OllirExprResult argumentCode = visit(argument);
            computation.append(argumentCode.getComputation());

            if(argument.getKind().equals("MethodExpr")) {
                String temp = OptUtils.getTemp();
                String invoke = argumentCode.getCode();
                Type argumentType = TypeUtils.getExprType(argument, table, currMethod);
                String ollirType = OptUtils.toOllirType(argumentType);
                if (objectType.getName().equals(table.getClassName()) && table.getMethods().contains(method)) {
                    argumentType = table.getParameters(method).get(i).getType();
                    ollirType = OptUtils.toOllirType(argumentType);
                }

                invoke = invoke.substring(0, invoke.lastIndexOf(".")) + ollirType + ";\n";

                computation.append(temp).append(ollirType).append(SPACE).append(ASSIGN)
                        .append(ollirType).append(SPACE).append(invoke);

                argCode.append(", ").append(temp).append(ollirType);
            }
            else {
                argCode.append(", ").append(argumentCode.getCode());
            }
        }
        return new OllirExprResult(argCode.toString(), computation.toString());
    }

    private OllirExprResult visitParensExpr(JmmNode node, Void unused) {
        return this.visit(node.getJmmChild(0));
    }

    private OllirExprResult visitNegExpr(JmmNode node, Void unused) {
        StringBuilder computation = new StringBuilder();

        var expr = this.visit(node.getJmmChild(0));

        computation.append(expr.getComputation());

        Type resType = TypeUtils.getExprType(node, table, currMethod);
        assert resType != null;
        String resOllirType = OptUtils.toOllirType(resType);
        String code = OptUtils.getTemp() + resOllirType;

        computation.append(code).append(SPACE)
                .append(ASSIGN).append(resOllirType)
                .append(" !.bool ")
                .append(expr.getCode()).append(END_STMT);


        return new OllirExprResult(code,computation);
    }

    private OllirExprResult visitThisExpr(JmmNode expr, Void unused) {
        return new OllirExprResult("this." + table.getClassName(), "");
    }


    /**
         * Default visitor. Visits every child node and return an empty result.
         *
         * @param node
         * @param unused
         * @return
         */
    private OllirExprResult defaultVisit(JmmNode node, Void unused) {

        for (JmmNode child : node.getChildren()) {
            visit(child);
        }

        return OllirExprResult.EMPTY;
    }


}

