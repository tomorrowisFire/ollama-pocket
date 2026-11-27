package com.ollamapocket.app;

public final class WorkbenchVisualPolicyTest {
    private static int n;
    private static void check(boolean ok){n++;if(!ok)throw new AssertionError("workbench visual "+n);}
    private static void contrast(int ink,int fill,double minimum){
        check(WorkbenchVisualPolicy.contrast(ink,fill)>=minimum);
    }
    public static void main(String[] args){
        for(boolean dark:new boolean[]{false,true}){
            WorkbenchVisualPolicy.Palette p=WorkbenchVisualPolicy.palette(dark);
            for(WorkbenchVisualPolicy.Role role:WorkbenchVisualPolicy.Role.values()){
                contrast(p.ink(role,true),p.fill(role,true),4.5);
                contrast(p.ink(role,false),p.fill(role,false),4.5);
                contrast(p.focusInk(role),p.fill(role,true),3);
                check(p.fill(role,false)==p.disabled&&p.ink(role,false)==p.disabledText);
            }
            for(boolean installed:new boolean[]{false,true})for(boolean selected:new boolean[]{false,true})for(boolean downloading:new boolean[]{false,true}){
                WorkbenchVisualPolicy.ModelStatus status=WorkbenchVisualPolicy.modelStatus(installed,selected,downloading);
                WorkbenchVisualPolicy.ModelStatus expected=downloading?WorkbenchVisualPolicy.ModelStatus.DOWNLOADING:
                    !installed?WorkbenchVisualPolicy.ModelStatus.AVAILABLE:selected?WorkbenchVisualPolicy.ModelStatus.ACTIVE:WorkbenchVisualPolicy.ModelStatus.INSTALLED;
                check(status==expected);
                contrast(p.modelStatusInk(status),p.modelStatusFill(status),4.5);
            }
            java.util.Set<Integer> statusFills=new java.util.HashSet<>();
            for(WorkbenchVisualPolicy.ModelStatus status:WorkbenchVisualPolicy.ModelStatus.values())statusFills.add(p.modelStatusFill(status));
            check(statusFills.size()==4);
            check(p.modelStatusInk(WorkbenchVisualPolicy.ModelStatus.ACTIVE)==p.primaryText);
            for(boolean positive:new boolean[]{false,true})for(boolean enabled:new boolean[]{false,true}){
                contrast(p.dialogInk(positive,enabled),p.dialogFill(enabled),4.5);
                check(p.dialogFill(enabled)==(enabled?p.surface:p.disabled));
            }
            contrast(p.focus,p.dialogFill(true),3);
            check(p.dialogInk(true,true)==p.primary&&p.dialogInk(false,true)==p.muted);
            check(p.dialogInk(true,false)==p.dialogInk(false,false));
            check((p.dialogRipple()>>>24)==0x24);
            check((p.dialogRipple()&0x00ffffff)==(p.primary&0x00ffffff));
            // F10A60: single-surface composer and stateful tool colors.
            contrast(p.text,p.surface,4.5);contrast(p.muted,p.surface,4.5);contrast(p.disabledText,p.surface,4.5);
            contrast(p.focus,p.surface,3);contrast(p.focus,p.canvas,3);
            for(boolean primary:new boolean[]{false,true})for(boolean enabled:new boolean[]{false,true}){
                int fill=p.composerActionFill(primary,enabled),ink=p.composerActionInk(primary,enabled);
                contrast(ink,fill,4.5);
                check(fill==(primary?(enabled?p.primaryFill:p.disabled):p.surface));
                check(ink==(!enabled?p.disabledText:primary?p.primaryText:p.text));
                if(enabled)contrast(p.composerActionFocus(primary),fill,3);
            }
            check(p.composerActionFill(true,true)!=p.composerActionFill(false,true));
            check(p.composerActionFill(true,false)!=p.composerActionFill(true,true));
            contrast(p.text,p.surface,4.5);contrast(p.text,p.field,4.5);
            contrast(p.text,p.canvas,4.5);contrast(p.text,p.bubble,4.5);
            contrast(p.text,p.accentSoft,4.5);contrast(p.primary,p.canvas,4.5);
            contrast(p.primary,p.surface,4.5);contrast(p.canvas,p.primary,4.5);
            contrast(p.muted,p.canvas,4.5);
            // F10A62: quiet signature palette, outlined quick actions and selected/recommended states.
            contrast(p.primary,p.bubble,4.9);
            contrast(p.focus,p.bubble,3);contrast(p.focus,p.accentSoft,3);
            check(p.text!=p.primary&&p.canvas!=p.surface&&p.surface!=p.field);
            contrast(p.disabledText,p.disabled,4.5);contrast(p.focus,p.surface,3);
            // F10A61: flat navigation rows remain readable when disabled and focused.
            contrast(p.disabledText,p.canvas,4.5);
            contrast(p.focus,p.canvas,3);
            check(p.canvas!=p.surface&&p.bubble!=p.field&&p.accentSoft!=p.field);
            check(p.primary==(dark?0xffe7c1ad:0xff79503e));
            check(p.primaryFill==(dark?0xffd4a58e:0xffe5bba6));
            check(p.primaryText==0xff30221d&&p.primaryFill!=p.primary);
            check(p.fill(WorkbenchVisualPolicy.Role.PRIMARY,true)==p.primaryFill);
            check(p.modelStatusFill(WorkbenchVisualPolicy.ModelStatus.ACTIVE)==p.primaryFill);
            check(p.composerActionFill(true,true)==p.primaryFill);
            contrast(p.primaryText,p.primaryFill,4.5);
            contrast(p.primaryOutline(),p.canvas,3);contrast(p.primaryOutline(),p.surface,3);
            check(WorkbenchVisualPolicy.contrast(p.primaryFill,p.surface)>=3||WorkbenchVisualPolicy.contrast(p.primaryOutline(),p.primaryFill)>=3);
            check(p.canvas==(dark?0xff191a19:0xfff9f8f5));
            check(p.focus==(dark?0xffddbcaa:0xff79503e));
            contrast(p.muted,p.surface,4.5);contrast(p.muted,p.field,4.5);
            contrast(p.disabledText,p.disabled,4.5);contrast(p.focus,p.field,3);contrast(p.focus,p.surface,3);
            check(WorkbenchVisualPolicy.darkFromPrimaryText(p.text)==dark);
            check(p.fill(WorkbenchVisualPolicy.Role.PRIMARY,true)!=p.fill(WorkbenchVisualPolicy.Role.SECONDARY,true));
            check(p.ink(WorkbenchVisualPolicy.Role.DESTRUCTIVE,true)!=p.ink(WorkbenchVisualPolicy.Role.SECONDARY,true));
        }
        check(WorkbenchVisualPolicy.textScale("large")==1.13f);
        check(WorkbenchVisualPolicy.textScale("extra")==1.26f);
        check(WorkbenchVisualPolicy.textScale("normal")==1f);
        check(WorkbenchVisualPolicy.textScale(null)==1f&&WorkbenchVisualPolicy.textScale("unexpected")==1f);
        check(WorkbenchVisualPolicy.MIN_TARGET_DP>=48&&WorkbenchVisualPolicy.RADIUS_DP>=8&&WorkbenchVisualPolicy.GAP_DP>=4);
        check(!WorkbenchVisualPolicy.darkFromPrimaryText(0xde000000));
        check(WorkbenchVisualPolicy.darkFromPrimaryText(0xdeffffff));
        check(Math.abs(WorkbenchVisualPolicy.contrast(0xff000000,0xffffffff)-21)<1e-9);
        System.out.println("PASS: "+n+" shared workspace palette and accessibility assertions");
    }
}
