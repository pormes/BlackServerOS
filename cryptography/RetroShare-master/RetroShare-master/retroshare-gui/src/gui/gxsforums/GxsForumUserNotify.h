/****************************************************************
 *  RetroShare is distributed under the following license:
 *
 *  Copyright (C) 2014 RetroShare Team
 *
 *  This program is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin Street, Fifth Floor,
 *  Boston, MA  02110-1301, USA.
 ****************************************************************/

#ifndef GXSFORUMUSERNOTIFY_H
#define GXSFORUMUSERNOTIFY_H

#include "gui/gxs/GxsUserNotify.h"

class GxsForumUserNotify : public GxsUserNotify
{
	Q_OBJECT

public:
	GxsForumUserNotify(RsGxsIfaceHelper *ifaceImpl, QObject *parent = 0);

	virtual bool hasSetting(QString *name, QString *group);

private:
	virtual QIcon getIcon();
	virtual QIcon getMainIcon(bool hasNew);
	virtual void iconClicked();
};

#endif // FORUMUSERNOTIFY_H
