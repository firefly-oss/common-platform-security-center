package com.firefly.intellij.lsp;

import com.intellij.lang.cacheBuilder.DefaultWordsScanner;
import com.intellij.lang.cacheBuilder.WordsScanner;
import com.intellij.lang.findUsages.FindUsagesProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Find Usages provider for Firefly.
 * Enables "Find Usages" functionality in IntelliJ.
 */
public class FireflyFindUsagesProvider implements FindUsagesProvider {
    
    @Override
    public @Nullable WordsScanner getWordsScanner() {
        // Return null to use default word scanner
        return null;
    }
    
    @Override
    public boolean canFindUsagesFor(@NotNull PsiElement psiElement) {
        // Allow find usages for any named element
        return psiElement instanceof PsiNamedElement;
    }
    
    @Override
    public @Nullable @NonNls String getHelpId(@NotNull PsiElement psiElement) {
        return null;
    }
    
    @Override
    public @Nls @NotNull String getType(@NotNull PsiElement element) {
        return "symbol";
    }
    
    @Override
    public @Nls @NotNull String getDescriptiveName(@NotNull PsiElement element) {
        if (element instanceof PsiNamedElement) {
            String name = ((PsiNamedElement) element).getName();
            return name != null ? name : "unknown";
        }
        return "unknown";
    }
    
    @Override
    public @Nls @NotNull String getNodeText(@NotNull PsiElement element, boolean useFullName) {
        return getDescriptiveName(element);
    }
}

