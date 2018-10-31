package lombok;

import com.sun.tools.javac.code.Flags;
import com.sun.tools.javac.tree.JCTree;
import lombok.javac.JavacNode;

public class Utils {

    public static boolean notAClass(JavacNode typeNode) {
        JCTree.JCClassDecl typeDecl = null;
        if (typeNode.get() instanceof JCTree.JCClassDecl) typeDecl = (JCTree.JCClassDecl) typeNode.get();
        long flags = typeDecl == null ? 0 : typeDecl.mods.flags;
        return typeDecl == null ||
                (flags & (Flags.INTERFACE | Flags.ENUM | Flags.ANNOTATION)) != 0;
    }
}
