package lombok.javac.handler;

import com.google.auto.service.AutoService;
import com.sun.tools.javac.tree.JCTree.*;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.Name;
import lombok.HelloWorld;
import lombok.core.AnnotationValues;
import lombok.javac.Javac;
import lombok.javac.JavacAnnotationHandler;
import lombok.javac.JavacNode;
import lombok.javac.JavacTreeMaker;
import lombok.javac.handlers.JavacHandlerUtil;

import java.lang.reflect.Modifier;

import static lombok.Utils.notAClass;

@AutoService(JavacAnnotationHandler.class)
@SuppressWarnings("restriction")
public class HandleHelloWorld extends JavacAnnotationHandler<HelloWorld> {

    public void handle(AnnotationValues<HelloWorld> annotation, JCAnnotation ast,
                       JavacNode annotationNode) {
        JavacHandlerUtil.deleteAnnotationIfNeccessary(annotationNode, HelloWorld.class);
        JavacNode typeNode = annotationNode.up();

        if (notAClass(typeNode)) {
            annotationNode.addError("@HelloWorld is only supported on a class.");
            return;
        }

        JCMethodDecl helloWorldMethod = createHelloWorld(typeNode);
        JavacHandlerUtil.injectMethod(typeNode, helloWorldMethod);
    }

    private JCMethodDecl createHelloWorld(JavacNode type) {
        JavacTreeMaker treeMaker = type.getTreeMaker();

        JCModifiers modifiers = treeMaker.Modifiers(Modifier.PUBLIC);
        List<JCTypeParameter> methodGenericTypes = List.nil();
        JCExpression methodType = treeMaker.TypeIdent(Javac.CTC_VOID);
        Name methodName = type.toName("helloWorld");
        List<JCVariableDecl> methodParameters = List.nil();
        List<JCExpression> methodThrows = List.nil();

        JCExpression printlnMethod =
                JavacHandlerUtil.chainDots(type, "System", "out", "println");
        List<JCExpression> printlnArgs = List.of(treeMaker.Literal("hello world"));
        JCMethodInvocation printlnInvocation =
                treeMaker.Apply(List.nil(), printlnMethod, printlnArgs);
        JCBlock methodBody =
                treeMaker.Block(0, List.of(treeMaker.Exec(printlnInvocation)));

        return treeMaker.MethodDef(
                modifiers,
                methodName,
                methodType,
                methodGenericTypes,
                methodParameters,
                methodThrows,
                methodBody,
                null
        );
    }
}
