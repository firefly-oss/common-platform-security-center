package com.firefly.intellij.lsp;

import com.intellij.ide.structureView.*;
import com.intellij.ide.util.treeView.smartTree.TreeElement;
import com.intellij.lang.PsiStructureViewFactory;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Structure View provider for Firefly using LSP.
 * Provides document outline/structure view by querying the LSP server for document symbols.
 */
public class FireflyStructureViewProvider implements PsiStructureViewFactory {
    
    private static final Logger LOG = LoggerFactory.getLogger(FireflyStructureViewProvider.class);
    
    @Override
    public @Nullable StructureViewBuilder getStructureViewBuilder(@NotNull PsiFile psiFile) {
        return new TreeBasedStructureViewBuilder() {
            @Override
            public @NotNull StructureViewModel createStructureViewModel(@Nullable Editor editor) {
                return new FireflyStructureViewModel(psiFile, editor);
            }
        };
    }
    
    /**
     * Structure view model for Firefly files.
     */
    private static class FireflyStructureViewModel extends StructureViewModelBase implements StructureViewModel.ElementInfoProvider {
        
        public FireflyStructureViewModel(@NotNull PsiFile psiFile, @Nullable Editor editor) {
            super(psiFile, editor, new FireflyStructureViewElement(psiFile));
        }
        
        @Override
        public boolean isAlwaysShowsPlus(StructureViewTreeElement element) {
            return false;
        }
        
        @Override
        public boolean isAlwaysLeaf(StructureViewTreeElement element) {
            return element instanceof FireflySymbolElement;
        }
    }
    
    /**
     * Root structure view element.
     */
    private static class FireflyStructureViewElement implements StructureViewTreeElement {
        
        private final PsiFile psiFile;
        
        public FireflyStructureViewElement(PsiFile psiFile) {
            this.psiFile = psiFile;
        }
        
        @Override
        public Object getValue() {
            return psiFile;
        }
        
        @Override
        public void navigate(boolean requestFocus) {
            // Navigation handled by children
        }
        
        @Override
        public boolean canNavigate() {
            return false;
        }
        
        @Override
        public boolean canNavigateToSource() {
            return false;
        }
        
        @Override
        public @NotNull ItemPresentation getPresentation() {
            return new ItemPresentation() {
                @Override
                public @Nullable String getPresentableText() {
                    return psiFile.getName();
                }
                
                @Override
                public @Nullable Icon getIcon(boolean unused) {
                    return psiFile.getIcon(0);
                }
            };
        }
        
        @Override
        public TreeElement @NotNull [] getChildren() {
            VirtualFile virtualFile = psiFile.getVirtualFile();
            if (virtualFile == null) {
                return EMPTY_ARRAY;
            }
            
            Project project = psiFile.getProject();
            FireflyLSPClient lspClient = project.getService(FireflyLSPClient.class);
            
            if (lspClient == null || !lspClient.isInitialized()) {
                return EMPTY_ARRAY;
            }
            
            try {
                // Request document symbols from LSP server
                TextDocumentIdentifier textDocument = new TextDocumentIdentifier(virtualFile.getUrl());
                DocumentSymbolParams params = new DocumentSymbolParams(textDocument);
                
                CompletableFuture<List<Either<SymbolInformation, DocumentSymbol>>> future = 
                    lspClient.getLanguageServer().getTextDocumentService().documentSymbol(params);
                
                List<Either<SymbolInformation, DocumentSymbol>> result = future.get(3, TimeUnit.SECONDS);
                
                if (result == null || result.isEmpty()) {
                    return EMPTY_ARRAY;
                }
                
                // Convert to tree elements
                List<TreeElement> elements = new ArrayList<>();
                for (Either<SymbolInformation, DocumentSymbol> either : result) {
                    if (either.isLeft()) {
                        SymbolInformation symbolInfo = either.getLeft();
                        elements.add(new FireflySymbolElement(psiFile, symbolInfo));
                    }
                }
                
                return elements.toArray(new TreeElement[0]);
                
            } catch (Exception e) {
                LOG.error("Error getting document symbols", e);
                return EMPTY_ARRAY;
            }
        }
    }
    
    /**
     * Symbol element in the structure view.
     */
    private static class FireflySymbolElement implements StructureViewTreeElement {
        
        private final PsiFile psiFile;
        private final SymbolInformation symbolInfo;
        
        public FireflySymbolElement(PsiFile psiFile, SymbolInformation symbolInfo) {
            this.psiFile = psiFile;
            this.symbolInfo = symbolInfo;
        }
        
        @Override
        public Object getValue() {
            return symbolInfo;
        }
        
        @Override
        public void navigate(boolean requestFocus) {
            // TODO: Navigate to symbol location
        }
        
        @Override
        public boolean canNavigate() {
            return true;
        }
        
        @Override
        public boolean canNavigateToSource() {
            return true;
        }
        
        @Override
        public @NotNull ItemPresentation getPresentation() {
            return new ItemPresentation() {
                @Override
                public @Nullable String getPresentableText() {
                    return symbolInfo.getName();
                }
                
                @Override
                public @Nullable Icon getIcon(boolean unused) {
                    return getIconForSymbolKind(symbolInfo.getKind());
                }
            };
        }
        
        @Override
        public TreeElement @NotNull [] getChildren() {
            return EMPTY_ARRAY;
        }
        
        private Icon getIconForSymbolKind(SymbolKind kind) {
            // Return appropriate icon based on symbol kind
            // For now, return null to use default
            return null;
        }
    }
}

