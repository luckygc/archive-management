package github.luckygc.am;

import github.luckygc.am.module.archive.item.ArchiveItem;
import jakarta.data.metamodel.TextAttribute;
import jakarta.data.restrict.Restriction;

public class CustomDemo {
    static void main() {
        TextAttribute<ArchiveItem> name = TextAttribute.of(ArchiveItem.class, "name");
        Restriction<ArchiveItem> x = name.contains("x");
        System.out.println(x.toString());
    }
}
