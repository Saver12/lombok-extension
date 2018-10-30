package lombok.javac.handler;

import java.lang.reflect.Modifier;

import com.sun.tools.javac.code.TypeTag;
import lombok.HelloWorld;
import lombok.core.AnnotationValues;
import lombok.javac.JavacAnnotationHandler;
import lombok.javac.JavacNode;
import lombok.javac.handlers.JavacHandlerUtil;

import org.mangosdk.spi.ProviderFor;

import com.sun.tools.javac.code.Flags;
import com.sun.tools.javac.tree.JCTree.JCAnnotation;
import com.sun.tools.javac.tree.JCTree.JCBlock;
import com.sun.tools.javac.tree.JCTree.JCClassDecl;
import com.sun.tools.javac.tree.JCTree.JCExpression;
import com.sun.tools.javac.tree.JCTree.JCMethodDecl;
import com.sun.tools.javac.tree.JCTree.JCMethodInvocation;
import com.sun.tools.javac.tree.JCTree.JCModifiers;
import com.sun.tools.javac.tree.JCTree.JCStatement;
import com.sun.tools.javac.tree.JCTree.JCTypeParameter;
import com.sun.tools.javac.tree.JCTree.JCVariableDecl;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.Name;

@ProviderFor(JavacAnnotationHandler.class)
@SuppressWarnings("restriction")
public class HandleHelloWorld implements JavacAnnotationHandler<HelloWorld> {

    public boolean handle(AnnotationValues<HelloWorld> annotation, JCAnnotation ast,
                          JavacNode annotationNode) {
        JavacHandlerUtil.markAnnotationAsProcessed(annotationNode, HelloWorld.class);
        JavacNode typeNode = annotationNode.up();

        if (notAClass(typeNode)) {
            annotationNode.addError("@HelloWorld is only supported on a class.");
            return false;
        }

        JCMethodDecl helloWorldMethod = createHelloWorld(typeNode);
        JavacHandlerUtil.injectMethod(typeNode, helloWorldMethod);
        return true;
    }

    private boolean notAClass(JavacNode typeNode) {
        JCClassDecl typeDecl = null;
        if (typeNode.get() instanceof JCClassDecl) typeDecl = (JCClassDecl) typeNode.get();
        long flags = typeDecl == null ? 0 : typeDecl.mods.flags;
        return typeDecl == null ||
                (flags & (Flags.INTERFACE | Flags.ENUM | Flags.ANNOTATION)) != 0;
    }

    private JCMethodDecl createHelloWorld(JavacNode type) {
        TreeMaker treeMaker = type.getTreeMaker();

        JCModifiers modifiers = treeMaker.Modifiers(Modifier.PUBLIC);
        List<JCTypeParameter> methodGenericTypes = List.nil();
        JCExpression methodType = treeMaker.TypeIdent(TypeTag.VOID);
        Name methodName = type.toName("helloWorld");
        List<JCVariableDecl> methodParameters = List.nil();
        List<JCExpression> methodThrows = List.nil();

        JCExpression printlnMethod =
                JavacHandlerUtil.chainDots(treeMaker, type, "System", "out", "println");
        List<JCExpression> printlnArgs = List.of(treeMaker.Literal("hello world"));
        JCMethodInvocation printlnInvocation =
                treeMaker.Apply(List.nil(), printlnMethod, printlnArgs);
        JCBlock methodBody =
                treeMaker.Block(0, List.of(treeMaker.Exec(printlnInvocation)));

        JCExpression defaultValue = null;

        return treeMaker.MethodDef(
                modifiers,
                methodName,
                methodType,
                methodGenericTypes,
                methodParameters,
                methodThrows,
                methodBody,
                defaultValue
        );
    }
}
