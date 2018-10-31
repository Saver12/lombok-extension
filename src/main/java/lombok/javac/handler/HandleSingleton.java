package lombok.javac.handler;

import com.sun.tools.javac.code.Flags;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.util.List;
import lombok.AccessLevel;
import lombok.Singleton;
import lombok.core.AST;
import lombok.core.AnnotationValues;
import lombok.javac.JavacAnnotationHandler;
import lombok.javac.JavacNode;
import lombok.javac.JavacTreeMaker;
import lombok.javac.handlers.HandleConstructor;
import lombok.javac.handlers.HandleGetter;
import org.mangosdk.spi.ProviderFor;

import static com.sun.tools.javac.tree.JCTree.*;
import static lombok.Utils.notAClass;
import static lombok.javac.handlers.JavacHandlerUtil.*;

@ProviderFor(JavacAnnotationHandler.class)
@SuppressWarnings("restriction")
public class HandleSingleton extends JavacAnnotationHandler<Singleton> {

    @Override
    public void handle(AnnotationValues<Singleton> annotation, JCAnnotation ast, JavacNode annotationNode) {
        deleteAnnotationIfNeccessary(annotationNode, Singleton.class);

        JavacNode typeNode = annotationNode.up();

        if (notAClass(typeNode)) {
            annotationNode.addError("@Singleton is only supported on a class.");
            return;
        }

        if (constructorExists(typeNode) != MemberExistsResult.NOT_EXISTS) {
            annotationNode.addError("There should be no constructors in a class");
            return;
        }

        if (fieldExists("instance", typeNode) != MemberExistsResult.NOT_EXISTS) {
            annotationNode.addError("Field \"instance\" already exists.");
            return;
        }

        JavacTreeMaker maker = typeNode.getTreeMaker();
        JCExpression type = chainDotsString(typeNode, typeNode.getName());
        JCNewClass newClass = maker.NewClass(null, List.nil(), type, List.nil(), null);
        JCVariableDecl fieldDecl = recursiveSetGeneratedBy(maker.VarDef(
                maker.Modifiers(Flags.PRIVATE | Flags.STATIC),
                typeNode.toName("instance"), type, newClass), annotationNode.get(), typeNode.getContext());
        injectFieldSuppressWarnings(typeNode, fieldDecl);

        new InstanceReturningConstructor().generateConstructor(typeNode, AccessLevel.PUBLIC, List.nil(), List.nil(),
                "getInstance", HandleConstructor.SkipIfConstructorExists.YES, null, annotationNode);
    }

    public static class InstanceReturningConstructor extends HandleConstructor {

        @Override
        public JCMethodDecl createStaticConstructor(String name, AccessLevel level, JavacNode typeNode,
                                                    List<JavacNode> fields, JCTree source) {
            JavacTreeMaker maker = typeNode.getTreeMaker();
            JCClassDecl type = (JCClassDecl) typeNode.get();
            JCModifiers mods = maker.Modifiers(Flags.STATIC | toJavacModifier(level));

            JavacNode instanceField = null;
            for (JavacNode field : typeNode.down()) {
                if (field.getKind() != AST.Kind.FIELD) continue;
                JCVariableDecl fieldDecl = (JCVariableDecl) field.get();

                if (fieldDecl.name.toString().equals("instance")) {
                    instanceField = field;
                    break;
                }
            }

            JCReturn returnStatement = (JCReturn) new HandleGetter().createSimpleGetterBody(maker, instanceField).get(0);
            JCBlock body = maker.Block(0, List.of(returnStatement));

            return recursiveSetGeneratedBy(maker.MethodDef(mods, typeNode.toName(name), maker.Ident(type.name),
                    List.nil(), List.nil(), List.nil(), body, null), source, typeNode.getContext());
        }
    }
}
