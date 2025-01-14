#include <list>

#include <QLayout>
#include <QDialogButtonBox>
#include "FriendSelectionDialog.h"

std::set<RsPgpId> FriendSelectionDialog::selectFriends_PGP(QWidget *parent,const QString& caption,const QString& header_text,
                            FriendSelectionWidget::Modus modus,
                            FriendSelectionWidget::ShowTypes show_type,
                            const std::set<RsPgpId>& pre_selected_ids)
{
    std::set<std::string> psids ;
    for(std::set<RsPgpId>::const_iterator it(pre_selected_ids.begin());it!=pre_selected_ids.end();++it)
        psids.insert( (*it).toStdString() ) ;

    FriendSelectionDialog dialog(parent,header_text,modus,show_type,FriendSelectionWidget::IDTYPE_GPG,psids) ;

    dialog.setWindowTitle(caption) ;

    if(QDialog::Rejected == dialog.exec())
        return std::set<RsPgpId>() ;

    std::set<RsPgpId> sids ;
    dialog.friends_widget->selectedIds<RsPgpId,FriendSelectionWidget::IDTYPE_GPG>(sids,false) ;

    return sids ;
}
std::set<RsPeerId> FriendSelectionDialog::selectFriends_SSL(QWidget *parent,const QString& caption,const QString& header_text,
                            FriendSelectionWidget::Modus modus,
                            FriendSelectionWidget::ShowTypes show_type,
                            const std::set<RsPeerId>& pre_selected_ids)
{
    std::set<std::string> psids ;
    for(std::set<RsPeerId>::const_iterator it(pre_selected_ids.begin());it!=pre_selected_ids.end();++it)
        psids.insert( (*it).toStdString() ) ;

    FriendSelectionDialog dialog(parent,header_text,modus,show_type,FriendSelectionWidget::IDTYPE_SSL,psids) ;

    dialog.setWindowTitle(caption) ;

    if(QDialog::Rejected == dialog.exec())
        return std::set<RsPeerId>() ;

    std::set<RsPeerId> sids ;
    dialog.friends_widget->selectedIds<RsPeerId,FriendSelectionWidget::IDTYPE_SSL>(sids,false) ;

    return sids ;
}
std::set<RsGxsId> FriendSelectionDialog::selectFriends_GXS(QWidget *parent,const QString& caption,const QString& header_text,
                            FriendSelectionWidget::Modus modus,
                            FriendSelectionWidget::ShowTypes show_type,
                            const std::set<RsGxsId>& pre_selected_ids)
{
    std::set<std::string> psids ;
    for(std::set<RsGxsId>::const_iterator it(pre_selected_ids.begin());it!=pre_selected_ids.end();++it)
        psids.insert( (*it).toStdString() ) ;

    FriendSelectionDialog dialog(parent,header_text,modus,show_type,FriendSelectionWidget::IDTYPE_SSL,psids) ;

    dialog.setWindowTitle(caption) ;

    if(QDialog::Rejected == dialog.exec())
        return std::set<RsGxsId>() ;

    std::set<RsGxsId> sids ;
    dialog.friends_widget->selectedIds<RsGxsId,FriendSelectionWidget::IDTYPE_GXS>(sids,false) ;

    return sids ;
}
FriendSelectionDialog::FriendSelectionDialog(QWidget *parent,const QString& header_text,
															FriendSelectionWidget::Modus modus,
															FriendSelectionWidget::ShowTypes show_type,
															FriendSelectionWidget::IdType pre_selected_id_type,
                                                            const std::set<std::string>& pre_selected_ids)
	: QDialog(parent)
{
	friends_widget = new FriendSelectionWidget(this) ;

	friends_widget->setHeaderText(header_text);
	friends_widget->setModus(modus) ;
	friends_widget->setShowType(show_type) ;
	friends_widget->start() ;
	friends_widget->setSelectedIds(pre_selected_id_type, pre_selected_ids, false);

	QLayout *l = new QVBoxLayout ;
	setLayout(l) ;
	
	QDialogButtonBox *buttonBox = new QDialogButtonBox(QDialogButtonBox::Ok | QDialogButtonBox::Cancel);

	connect(buttonBox, SIGNAL(accepted()), this, SLOT(accept()));
	connect(buttonBox, SIGNAL(rejected()), this, SLOT(reject()));

	l->addWidget(friends_widget) ;
	l->addWidget(buttonBox) ;
	l->update() ;
}

FriendSelectionDialog::~FriendSelectionDialog()
{
	delete friends_widget ;
}

