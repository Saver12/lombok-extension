package lombok;

import com.sun.tools.javac.code.Flags;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.Name;
import lombok.core.AST;
import lombok.core.AnnotationValues;
import lombok.javac.JavacNode;
import lombok.javac.JavacTreeMaker;
import lombok.javac.handlers.HandleGetter;
import lombok.javac.handlers.JavacHandlerUtil;

import static lombok.javac.handlers.JavacHandlerUtil.*;

public class Utils {

    public static boolean notAClass(JavacNode typeNode) {
        JCTree.JCClassDecl typeDecl = null;
        if (typeNode.get() instanceof JCTree.JCClassDecl) typeDecl = (JCTree.JCClassDecl) typeNode.get();
        long flags = typeDecl == null ? 0 : typeDecl.mods.flags;
        return typeDecl == null ||
                (flags & (Flags.INTERFACE | Flags.ENUM | Flags.ANNOTATION)) != 0;
    }

    public static JCTree.JCExpression createFieldAccessor(JavacTreeMaker maker, JavacNode field, JavacHandlerUtil.FieldAccess fieldAccess) {
        return createFieldAccessor(maker, field, fieldAccess, null);
    }

    private static JCTree.JCExpression createFieldAccessor(JavacTreeMaker maker, JavacNode field,
                                                           JavacHandlerUtil.FieldAccess fieldAccess,
                                                           JCTree.JCExpression receiver) {
        boolean lookForGetter = lookForGetter(field, fieldAccess);

        GetterMethod getter = lookForGetter ? findGetter(field) : null;
        JCTree.JCVariableDecl fieldDecl = (JCTree.JCVariableDecl) field.get();

        if (getter == null) {
            if (receiver == null) {
                if ((fieldDecl.mods.flags & Flags.STATIC) == 0) {
                    receiver = maker.Ident(field.toName("this"));
                } else {
                    JavacNode containerNode = field.up();
                    if (containerNode != null && containerNode.get() instanceof JCTree.JCClassDecl) {
                        JCTree.JCClassDecl container = (JCTree.JCClassDecl) field.up().get();
                        receiver = maker.Ident(container.name);
                    }
                }
            }

            return receiver == null ? maker.Ident(fieldDecl.name) : maker.Select(receiver, fieldDecl.name);
        }

        if (receiver == null) receiver = maker.Ident(field.toName("this"));
        return maker.Apply(List.nil(),
                maker.Select(receiver, getter.name), List.nil());
    }

    private static boolean lookForGetter(JavacNode field, JavacHandlerUtil.FieldAccess fieldAccess) {
        if (fieldAccess == JavacHandlerUtil.FieldAccess.GETTER) return true;
        if (fieldAccess == JavacHandlerUtil.FieldAccess.ALWAYS_FIELD) return false;

        // If @Getter(lazy = true) is used, then using it is mandatory.
        for (JavacNode child : field.down()) {
            if (child.getKind() != AST.Kind.ANNOTATION) continue;
            if (annotationTypeMatches(Getter.class, child)) {
                AnnotationValues<Getter> ann = createAnnotation(Getter.class, child);
                if (ann.getInstance().lazy()) return true;
            }
        }
        return false;
    }

    private static GetterMethod findGetter(JavacNode field) {
        JCTree.JCVariableDecl decl = (JCTree.JCVariableDecl) field.get();
        JavacNode typeNode = field.up();
        for (String potentialGetterName : toAllGetterNames(field)) {
            for (JavacNode potentialGetter : typeNode.down()) {
                if (potentialGetter.getKind() != AST.Kind.METHOD) continue;
                JCTree.JCMethodDecl method = (JCTree.JCMethodDecl) potentialGetter.get();
                if (!method.name.toString().equalsIgnoreCase(potentialGetterName)) continue;
                /* static getX() methods don't count. */
                if ((method.mods.flags & Flags.STATIC) != 0) continue;
                /* Nor do getters with a non-empty parameter list. */
                if (method.params != null && method.params.size() > 0) continue;
                return new GetterMethod(method.name, method.restype);
            }
        }

        // Check if the field has a @Getter annotation.

        boolean hasGetterAnnotation = false;

        for (JavacNode child : field.down()) {
            if (child.getKind() == AST.Kind.ANNOTATION && annotationTypeMatches(Getter.class, child)) {
                AnnotationValues<Getter> ann = createAnnotation(Getter.class, child);
                if (ann.getInstance().value() == AccessLevel.NONE) return null;   //Definitely WONT have a getter.
                hasGetterAnnotation = true;
            }
        }

        // Check if the class has a @Getter annotation.

        if (!hasGetterAnnotation && new HandleGetter().fieldQualifiesForGetterGeneration(field)) {
            //Check if the class has @Getter or @Data annotation.

            JavacNode containingType = field.up();
            if (containingType != null) for (JavacNode child : containingType.down()) {
                if (child.getKind() == AST.Kind.ANNOTATION && annotationTypeMatches(Data.class, child))
                    hasGetterAnnotation = true;
                if (child.getKind() == AST.Kind.ANNOTATION && annotationTypeMatches(Getter.class, child)) {
                    AnnotationValues<Getter> ann = createAnnotation(Getter.class, child);
                    if (ann.getInstance().value() == AccessLevel.NONE) return null;   //Definitely WONT have a getter.
                    hasGetterAnnotation = true;
                }
            }
        }

        if (hasGetterAnnotation) {
            String getterName = toGetterName(field);
            if (getterName == null) return null;
            return new GetterMethod(field.toName(getterName), decl.vartype);
        }

        return null;
    }

    private static class GetterMethod {
        private final Name name;
        private final JCTree.JCExpression type;

        GetterMethod(Name name, JCTree.JCExpression type) {
            this.name = name;
            this.type = type;
        }
    }
}
