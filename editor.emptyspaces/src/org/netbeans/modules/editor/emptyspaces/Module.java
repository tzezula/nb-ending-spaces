/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.netbeans.modules.editor.emptyspaces;

import org.openide.modules.ModuleInstall;

/**
 *
 * @author Tomas Zezula
 */
public class Module extends ModuleInstall {

    @Override
    public void restored() {
        super.restored();
        SpacesHint.getDefault().start();
    }
}
