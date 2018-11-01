package lombok.javac.handler;

import com.google.auto.service.AutoService;
import com.sun.tools.javac.code.Flags;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.ListBuffer;
import lombok.AccessLevel;
import lombok.core.AST;
import lombok.core.AnnotationValues;
import lombok.javac.JavacAnnotationHandler;
import lombok.javac.JavacNode;
import lombok.javac.JavacTreeMaker;
import lombok.javac.handlers.HandleConstructor;
import lombok.singleton.Singleton;
import lombok.singleton.Type;

import static com.sun.tools.javac.tree.JCTree.*;
import static lombok.Utils.createFieldAccessor;
import static lombok.Utils.notAClass;
import static lombok.javac.Javac.CTC_BOT;
import static lombok.javac.Javac.CTC_EQUAL;
import static lombok.javac.handlers.HandleLog.selfType;
import static lombok.javac.handlers.JavacHandlerUtil.*;

@AutoService(JavacAnnotationHandler.class)
@SuppressWarnings("restriction")
public class HandleSingleton extends JavacAnnotationHandler<Singleton> {

    private Type annotationType;

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
        annotationType = annotation.getInstance().value();
        JCNewClass newClass = null;
        if (annotationType == Type.EAGER) {
            newClass = maker.NewClass(
                    null,
                    List.nil(),
                    type,
                    List.nil(),
                    null
            );
        }

        JCVariableDecl instanceVariable = maker.VarDef(
                maker.Modifiers(Flags.PRIVATE | Flags.STATIC | (annotationType == Type.DCL ? Flags.VOLATILE : 0)),
                typeNode.toName("instance"),
                type,
                newClass
        );

        JCVariableDecl fieldDecl = recursiveSetGeneratedBy(
                instanceVariable,
                annotationNode.get(),
                typeNode.getContext()
        );

        injectFieldSuppressWarnings(typeNode, fieldDecl);

        new InstanceReturningConstructor().generateConstructor(
                typeNode,
                AccessLevel.PUBLIC,
                List.nil(),
                List.nil(),
                "getInstance",
                HandleConstructor.SkipIfConstructorExists.YES,
                null,
                annotationNode
        );
    }

    private class InstanceReturningConstructor extends HandleConstructor {

        @Override
        public JCMethodDecl createStaticConstructor(String name, AccessLevel level, JavacNode typeNode,
                                                    List<JavacNode> fields, JCTree source) {

            JavacTreeMaker maker = typeNode.getTreeMaker();
            JCClassDecl type = (JCClassDecl) typeNode.get();
            JCModifiers mods = maker.Modifiers(Flags.STATIC | toJavacModifier(level) |
                    (annotationType == Type.LAZY ? Flags.SYNCHRONIZED : 0));

            JavacNode instanceField = null;
            for (JavacNode field : typeNode.down()) {
                if (field.getKind() != AST.Kind.FIELD) continue;
                JCVariableDecl fieldDecl = (JCVariableDecl) field.get();
                if (fieldDecl.name.toString().equals("instance")) {
                    instanceField = field;
                    break;
                }
            }

            ListBuffer<JCStatement> statements = new ListBuffer<>();

            JCExpression fieldAccessor = createFieldAccessor(maker, instanceField, FieldAccess.ALWAYS_FIELD);

            if (annotationType != Type.EAGER) addLazyInitialization(maker, typeNode, fieldAccessor, statements);

            JCReturn returnStatement = maker.Return(fieldAccessor);
            statements.append(returnStatement);
            JCBlock body = maker.Block(0, statements.toList());

            JCMethodDecl methodDef = maker.MethodDef(
                    mods,
                    typeNode.toName(name),
                    maker.Ident(type.name),
                    List.nil(),
                    List.nil(),
                    List.nil(),
                    body,
                    null);

            return recursiveSetGeneratedBy(
                    methodDef,
                    source,
                    typeNode.getContext()
            );
        }

        private void addLazyInitialization(JavacTreeMaker maker, JavacNode typeNode, JCExpression fieldAccessor,
                                           ListBuffer<JCStatement> statements) {
            JCExpression thisEqualsNull = maker.Binary(
                    CTC_EQUAL,
                    fieldAccessor,
                    maker.Literal(CTC_BOT, null)
            );

            JCNewClass newClass = maker.NewClass(
                    null,
                    List.nil(),
                    chainDotsString(typeNode, typeNode.getName()),
                    List.nil(),
                    null
            );

            JCAssign assign = maker.Assign(fieldAccessor, newClass);

            JCIf jcIf = maker.If(thisEqualsNull, maker.Exec(assign), null);

            if (annotationType == Type.DCL) {
                JCSynchronized aSynchronized = maker.Synchronized(
                        selfType(typeNode),
                        maker.Block(0, List.of(jcIf))
                );
                jcIf = maker.If(thisEqualsNull, aSynchronized, null);
            }

            statements.append(jcIf);
        }
    }
}
