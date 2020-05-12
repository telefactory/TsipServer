-- MySQL dump 10.13  Distrib 5.7.30, for Linux (x86_64)
--
-- Host: localhost    Database: customers
-- ------------------------------------------------------
-- Server version	5.7.30-0ubuntu0.16.04.1

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!40101 SET NAMES utf8 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

--
-- Table structure for table `Announcement`
--

DROP TABLE IF EXISTS `Announcement`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `Announcement` (
  `CF_ID` int(11) NOT NULL,
  `MID` int(11) NOT NULL,
  `Description` varchar(255) DEFAULT NULL,
  `UseServiceNumber` int(1) DEFAULT '0' COMMENT '0 - false, 1- true, find recording in Service_Announcment table',
  `FileName` varchar(255) DEFAULT NULL COMMENT 'Filename of file to be played',
  `CommonRecordingID` int(11) NOT NULL DEFAULT '0' COMMENT 'ID of common recording to be played (if FileName is null)',
  `CommonRecording2ID` int(11) NOT NULL DEFAULT '0' COMMENT 'ID of second common recording to be played (if FileName is null)',
  `Dtmf` varchar(50) DEFAULT NULL COMMENT 'A dtmf string to be played back',
  `AnswerCallPolicy` varchar(50) NOT NULL DEFAULT 'NO_ANSWER' COMMENT 'NO_ANSWER. BEFORE. AFTER',
  `WaitForComplete` int(1) DEFAULT '0' COMMENT '0 - false, 1- true',
  `IgnoreIfPrepaid` int(1) DEFAULT '0' COMMENT '0 - false, 1- true',
  `NextMID` int(11) DEFAULT '0',
  PRIMARY KEY (`MID`,`CF_ID`)
) ENGINE=InnoDB DEFAULT CHARSET=latin1 COMMENT='Contains paramters for playing a voice file, common or personal	';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `Application`
--

DROP TABLE IF EXISTS `Application`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `Application` (
  `APP_ID` int(11) NOT NULL AUTO_INCREMENT,
  `ApplicationName` varchar(45) DEFAULT NULL,
  PRIMARY KEY (`APP_ID`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=latin1 COMMENT='??	';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `BlackList`
--

DROP TABLE IF EXISTS `BlackList`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `BlackList` (
  `bl_ID` int(11) NOT NULL AUTO_INCREMENT,
  `A_Number` varchar(50) NOT NULL DEFAULT '0' COMMENT 'The number that is to be blacklistet',
  `B_Number` varchar(50) NOT NULL DEFAULT '0' COMMENT 'There service that the blacklisting relates to',
  `ServiceCategory` varchar(50) DEFAULT NULL COMMENT 'Block all calls to services of this category',
  `Description` varchar(255) DEFAULT '0' COMMENT 'Description',
  `Reason` varchar(255) DEFAULT '0' COMMENT 'The reason for blacklisting',
  `RejectCall` int(1) NOT NULL DEFAULT '1' COMMENT '0 - False, 1 - True.',
  `CommonRecordingID` int(5) DEFAULT '0' COMMENT 'If RecjectCall false, play this recording',
  `CallCount` int(11) DEFAULT '0' COMMENT 'Increments for each blacklisted call',
  `LastHitDate` datetime DEFAULT NULL COMMENT 'Time of last blacklisting hit',
  `StartDate` datetime NOT NULL COMMENT 'Time for blacklisting to start',
  `EndDate` datetime DEFAULT NULL COMMENT 'Time for blacklisting to end',
  PRIMARY KEY (`bl_ID`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `Broadcast`
--

DROP TABLE IF EXISTS `Broadcast`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `Broadcast` (
  `CF_ID` int(11) NOT NULL DEFAULT '0',
  `MID` int(11) NOT NULL DEFAULT '0',
  `PinCode` varchar(50) NOT NULL DEFAULT '0' COMMENT 'PIN code to access this feature',
  `RecordedFileName` varchar(100) NOT NULL DEFAULT '0' COMMENT 'The path of the recorded file',
  `DefaultFileName` varchar(100) DEFAULT '0' COMMENT 'The path of default recorded file.',
  `RecordedFileActive` int(1) NOT NULL DEFAULT '0' COMMENT '0 - False; 1 - True',
  PRIMARY KEY (`CF_ID`,`MID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `CallFlow`
--

DROP TABLE IF EXISTS `CallFlow`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `CallFlow` (
  `CF_ID` int(11) NOT NULL AUTO_INCREMENT,
  `FirstMID` int(11) DEFAULT NULL COMMENT 'First module of call flow',
  `Description` varchar(50) DEFAULT NULL,
  `StartDate` date NOT NULL,
  `ChangeDate` date DEFAULT NULL,
  `EndDate` date DEFAULT NULL,
  PRIMARY KEY (`CF_ID`)
) ENGINE=InnoDB AUTO_INCREMENT=154 DEFAULT CHARSET=latin1 COMMENT='The head of a call flow which is a linked list of modules.';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!50003 SET @saved_cs_client      = @@character_set_client */ ;
/*!50003 SET @saved_cs_results     = @@character_set_results */ ;
/*!50003 SET @saved_col_connection = @@collation_connection */ ;
/*!50003 SET character_set_client  = utf8 */ ;
/*!50003 SET character_set_results = utf8 */ ;
/*!50003 SET collation_connection  = utf8_general_ci */ ;
/*!50003 SET @saved_sql_mode       = @@sql_mode */ ;
/*!50003 SET sql_mode              = '' */ ;
DELIMITER ;;
/*!50003 CREATE*/ /*!50017 DEFINER=`tsip`@`localhost`*/ /*!50003 TRIGGER CallFlow_trigger
BEFORE INSERT ON CallFlow
FOR EACH ROW
BEGIN
    IF (ISNULL(NEW.CF_ID) OR NEW.CF_ID = 0) THEN
        SET NEW.CF_ID := (
            SELECT COALESCE( MAX( CF_ID ), 0 ) + 1 FROM CallFlow
        );
    END IF;
    SET NEW.StartDate := COALESCE( NEW.StartDate, now() );
END */;;
DELIMITER ;
/*!50003 SET sql_mode              = @saved_sql_mode */ ;
/*!50003 SET character_set_client  = @saved_cs_client */ ;
/*!50003 SET character_set_results = @saved_cs_results */ ;
/*!50003 SET collation_connection  = @saved_col_connection */ ;

--
-- Table structure for table `CallFlowTemplates`
--

DROP TABLE IF EXISTS `CallFlowTemplates`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `CallFlowTemplates` (
  `T_ID` int(11) NOT NULL AUTO_INCREMENT,
  `CF_ID` int(11) NOT NULL DEFAULT '0',
  `TemplateName` varchar(50) NOT NULL DEFAULT '0',
  `TemplateDescription` varchar(512) DEFAULT '0',
  PRIMARY KEY (`T_ID`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=latin1 COMMENT='CallFlow templates names and descriptions';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `ChangeLog`
--

DROP TABLE IF EXISTS `ChangeLog`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `ChangeLog` (
  `CL_ID` int(11) NOT NULL AUTO_INCREMENT,
  `CF_ID` int(11) NOT NULL DEFAULT '0',
  `ServiceNumber` varchar(50) DEFAULT '0',
  `ChangedBy` varchar(50) NOT NULL DEFAULT '0',
  `ChangeDate` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `ChangeType` varchar(50) NOT NULL DEFAULT '0',
  `Description` varchar(50) DEFAULT '0',
  KEY `Index 1` (`CL_ID`),
  KEY `CF_ID_Description` (`CF_ID`,`Description`,`ChangeDate`)
) ENGINE=InnoDB AUTO_INCREMENT=2738 DEFAULT CHARSET=latin1 COMMENT='TBD';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `CommonRecordings`
--

DROP TABLE IF EXISTS `CommonRecordings`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `CommonRecordings` (
  `CR_ID` int(11) NOT NULL AUTO_INCREMENT,
  `Identifier` varchar(55) NOT NULL DEFAULT '0' COMMENT 'Not Used',
  `Description` varchar(255) NOT NULL DEFAULT '0' COMMENT 'Can be used by web front-end',
  `FileName` varchar(255) NOT NULL DEFAULT '0' COMMENT 'Full filename on asterisk server',
  `Category` varchar(50) NOT NULL DEFAULT '0' COMMENT 'Can be used by web front-end',
  PRIMARY KEY (`CR_ID`)
) ENGINE=InnoDB AUTO_INCREMENT=26 DEFAULT CHARSET=latin1 COMMENT='Identifier and filename of common recordings used by TSIP';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `Conference`
--

DROP TABLE IF EXISTS `Conference`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `Conference` (
  `CF_ID` int(11) NOT NULL,
  `MID` int(11) NOT NULL,
  `Description` varchar(255) NOT NULL,
  `ConferenceId` varchar(45) DEFAULT NULL,
  `ConfPIN` varchar(45) DEFAULT NULL,
  `StartDate` date NOT NULL,
  `EndDate` date DEFAULT NULL,
  PRIMARY KEY (`MID`,`CF_ID`)
) ENGINE=InnoDB DEFAULT CHARSET=latin1 COMMENT='TBD	';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `Customer`
--

DROP TABLE IF EXISTS `Customer`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `Customer` (
  `C_ID` int(11) NOT NULL AUTO_INCREMENT,
  `CustomerName` varchar(45) DEFAULT NULL,
  `ContactName` varchar(45) DEFAULT NULL,
  `ContactNumber` varchar(45) DEFAULT NULL,
  `StartDate` date DEFAULT NULL COMMENT 'If before start date, no calls will be handled',
  `EndDate` date DEFAULT NULL COMMENT 'If past end date, no calls will be handled',
  `ExternalCustomerId` int(11) DEFAULT NULL COMMENT 'External Id referencing customers_id in billing database',
  PRIMARY KEY (`C_ID`)
) ENGINE=InnoDB AUTO_INCREMENT=28 DEFAULT CHARSET=latin1 COMMENT='List of customers where each customer can own one or more service numbers';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!50003 SET @saved_cs_client      = @@character_set_client */ ;
/*!50003 SET @saved_cs_results     = @@character_set_results */ ;
/*!50003 SET @saved_col_connection = @@collation_connection */ ;
/*!50003 SET character_set_client  = utf8 */ ;
/*!50003 SET character_set_results = utf8 */ ;
/*!50003 SET collation_connection  = utf8_general_ci */ ;
/*!50003 SET @saved_sql_mode       = @@sql_mode */ ;
/*!50003 SET sql_mode              = '' */ ;
DELIMITER ;;
/*!50003 CREATE*/ /*!50017 DEFINER=`tsip`@`localhost`*/ /*!50003 TRIGGER customer_start_date
BEFORE INSERT ON Customer
FOR EACH ROW
BEGIN
    SET NEW.StartDate := COALESCE( NEW.StartDate, now() );
END */;;
DELIMITER ;
/*!50003 SET sql_mode              = @saved_sql_mode */ ;
/*!50003 SET character_set_client  = @saved_cs_client */ ;
/*!50003 SET character_set_results = @saved_cs_results */ ;
/*!50003 SET collation_connection  = @saved_col_connection */ ;

--
-- Table structure for table `CustomerDestinationNumbers`
--

DROP TABLE IF EXISTS `CustomerDestinationNumbers`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `CustomerDestinationNumbers` (
  `CDN_ID` int(11) NOT NULL AUTO_INCREMENT,
  `C_ID` int(11) NOT NULL DEFAULT '0',
  `DestinationNumber` varchar(50) NOT NULL DEFAULT '0',
  PRIMARY KEY (`CDN_ID`)
) ENGINE=InnoDB DEFAULT CHARSET=latin1 COMMENT='??';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `CustomerSpecific`
--

DROP TABLE IF EXISTS `CustomerSpecific`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `CustomerSpecific` (
  `CF_ID` int(11) NOT NULL DEFAULT '0',
  `MID` int(11) NOT NULL DEFAULT '0',
  `CustomerSpecificModule` varchar(255) DEFAULT NULL COMMENT 'Fixed name of specific module',
  `Description` varchar(255) DEFAULT NULL COMMENT 'Free text desciption',
  `NextMID` int(11) DEFAULT NULL,
  PRIMARY KEY (`CF_ID`,`MID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `DialInManagementNumbers`
--

DROP TABLE IF EXISTS `DialInManagementNumbers`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `DialInManagementNumbers` (
  `DM_ID` int(11) NOT NULL AUTO_INCREMENT,
  `NR_ID` int(11) NOT NULL DEFAULT '0' COMMENT 'Link to Service Number',
  `Number` varchar(50) NOT NULL DEFAULT '0' COMMENT 'Caller number',
  PRIMARY KEY (`DM_ID`)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=latin1 COMMENT='Links service numbers to legal caller number for one-step DIM. Only PIN needed.\r\n';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `DialOut`
--

DROP TABLE IF EXISTS `DialOut`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `DialOut` (
  `DO_ID` int(11) NOT NULL AUTO_INCREMENT,
  `CF_ID` int(11) NOT NULL DEFAULT '0',
  `MID` int(11) NOT NULL DEFAULT '0',
  `CallerID` varchar(50) NOT NULL DEFAULT '0' COMMENT 'The a-number of the outgoing call',
  `PIN` varchar(10) DEFAULT NULL COMMENT 'The PIN code to give access to this feature. NULL if unused',
  `NextMID` int(11) DEFAULT '0',
  PRIMARY KEY (`DO_ID`),
  KEY `CF_ID_MID` (`CF_ID`,`MID`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8 COMMENT='In this madule a user can enter via dtmf a destination number';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `Email`
--

DROP TABLE IF EXISTS `Email`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `Email` (
  `E_ID` int(11) NOT NULL AUTO_INCREMENT,
  `CF_ID` int(11) NOT NULL DEFAULT '0',
  `MID` int(11) NOT NULL DEFAULT '0',
  `ToAddress` varchar(255) NOT NULL DEFAULT '0' COMMENT 'comma seperated',
  `FromAddress` varchar(255) NOT NULL DEFAULT '0',
  `Subject` varchar(255) NOT NULL DEFAULT '0' COMMENT '%A is a-number, %B is b-number, %R is reason, %T is time',
  `Content` varchar(1024) NOT NULL DEFAULT '0' COMMENT '%A is a-number, %B is b-number, %R is reason, , %T is time',
  `NextMID` int(11) NOT NULL DEFAULT '0',
  PRIMARY KEY (`E_ID`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `GlobalSetting`
--

DROP TABLE IF EXISTS `GlobalSetting`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `GlobalSetting` (
  `GS_ID` int(11) NOT NULL AUTO_INCREMENT,
  `MaintenanceDuration` int(11) DEFAULT NULL COMMENT 'if > 0 then maintenance is enabled',
  `MaintenancePattern` varchar(50) DEFAULT '' COMMENT 'All numbrs if empty, or startsWith',
  PRIMARY KEY (`GS_ID`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `HuntGroup`
--

DROP TABLE IF EXISTS `HuntGroup`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `HuntGroup` (
  `HG_ID` int(11) NOT NULL AUTO_INCREMENT,
  `CF_ID` int(11) NOT NULL,
  `MID` int(11) NOT NULL,
  `Description` varchar(255) DEFAULT '',
  `HuntGroupStrategy` varchar(45) DEFAULT NULL COMMENT 'LINEAR, CIRCULAR, RING_ALL, RANDOM',
  `ActiveHgListId` int(11) DEFAULT '0' COMMENT 'Id of Active HuntGroup_List',
  `ActiveHgMemberId` int(11) DEFAULT '0' COMMENT 'Id of Active HG Member, overrides Schedule.',
  `OverrideDestination` varchar(80) DEFAULT NULL COMMENT 'Bypass huntgroup and route to destination if a valid number here',
  `WaitMusic` varchar(45) DEFAULT NULL COMMENT 'Not used now',
  `AnswerCallPolicy` varchar(45) DEFAULT 'NO_ANSWER' COMMENT 'NO_ANSWER, BEFORE, AFTER',
  `RingTonePolicy` varchar(45) DEFAULT 'FAKE_RINGING' COMMENT 'TRUE_RINGING, FAKE_RINGING, MUSIC',
  `UseTrueANumber` int(1) DEFAULT '1' COMMENT '0 - false, 1- true',
  `RingingTimeout` int(11) DEFAULT '0' COMMENT 'Time to allow HG to ring before overflow in minutes',
  `ConnectedCallTimeout` int(11) DEFAULT '0' COMMENT 'Time to allow call to be connected in minutes (not used)',
  `AddToConference` int(1) DEFAULT '0' COMMENT '0 - false, 1- true  (RingAll only) - Called parties are added to a conference upon answer',
  `DtmfAcceptCall` int(1) DEFAULT '0' COMMENT '0 - false, 1- true   (RingAll only) - Called party must enter DTMF 5 to accept call.',
  `AnnounceCall` int(1) DEFAULT '0' COMMENT '0 - false, 1- true  (RingAll only) - Announce the call when called party answers',
  `AnnounceCallMsgType` int(11) DEFAULT '0' COMMENT '0 - Standard, 1- User Defined (manual) (RingAll only)',
  `StatusSmsToHead` int(1) DEFAULT '0' COMMENT '0 - false, 1- true; Send status SMS to head of list',
  `MissedCallSms` int(1) DEFAULT '0' COMMENT '0 - false, 1- true; Send missed call SMS to head of list',
  `OverflowMID` int(11) DEFAULT '0' COMMENT 'LINEAR: All members tried. CIRCULAR: Hunt Group RingingTimeout reached',
  `BusyMID` int(11) DEFAULT '0' COMMENT 'RingAll busy',
  `NextMID` int(11) DEFAULT '0' COMMENT 'NextMID after successful call',
  `StartDate` date DEFAULT NULL,
  `EndDate` date DEFAULT NULL,
  `ChangeDate` date DEFAULT NULL,
  PRIMARY KEY (`HG_ID`),
  KEY `CF_ID_MID` (`CF_ID`,`MID`)
) ENGINE=InnoDB AUTO_INCREMENT=9 DEFAULT CHARSET=latin1 ROW_FORMAT=COMPACT COMMENT='Contains all parameters for a given hunt group. Points to active call list';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!50003 SET @saved_cs_client      = @@character_set_client */ ;
/*!50003 SET @saved_cs_results     = @@character_set_results */ ;
/*!50003 SET @saved_col_connection = @@collation_connection */ ;
/*!50003 SET character_set_client  = utf8mb4 */ ;
/*!50003 SET character_set_results = utf8mb4 */ ;
/*!50003 SET collation_connection  = utf8mb4_general_ci */ ;
/*!50003 SET @saved_sql_mode       = @@sql_mode */ ;
/*!50003 SET sql_mode              = 'ONLY_FULL_GROUP_BY,STRICT_TRANS_TABLES,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION' */ ;
DELIMITER ;;
/*!50003 CREATE*/ /*!50017 DEFINER=`tfsven`@`localhost`*/ /*!50003 TRIGGER `HuntGroup_Trigger` BEFORE UPDATE ON `HuntGroup` FOR EACH ROW BEGIN

	IF not isnull(new.ActiveHgListId) AND isnull(old.ActiveHgListId) THEN
		SET new.changeDate = Now();
		INSERT INTO ChangeLog( CF_ID, ChangedBy, ChangeType, Description )
		SELECT new.CF_ID, "Trigger", "HG List", CONCAT(new.ActiveHgListId, " Activated");
	END IF;
	
	IF new.ActiveHgListId <> old.ActiveHgListId THEN
		SET new.changeDate = Now();
		INSERT INTO ChangeLog( CF_ID, ChangedBy, ChangeType, Description )
		SELECT new.CF_ID, "Trigger", "HG List", CONCAT(new.ActiveHgListId, " Activated");
	END IF;
	
	IF NOT ISNULL(new.ActiveHgMemberId) AND ISNULL(old.ActiveHgMemberId) THEN
		SET new.changeDate = Now();
		INSERT INTO ChangeLog( CF_ID, ChangedBy, ChangeType, Description )
		SELECT new.CF_ID, "Trigger", "HG Member", CONCAT(new.ActiveHgMemberId, " Activated");
	END IF;
	
	IF new.ActiveHgMemberId <> old.ActiveHgMemberId THEN
		SET new.changeDate = Now();
		INSERT INTO ChangeLog( CF_ID, ChangedBy, ChangeType, Description )
		SELECT new.CF_ID, "Trigger", "HG Member", CONCAT(new.ActiveHgMemberId, " Activated");
	END IF;
	
	IF NOT ISNULL(new.OverrideDestination) AND ISNULL(old.OverrideDestination) THEN
		SET new.changeDate = Now();
		INSERT INTO ChangeLog( CF_ID, ChangedBy, ChangeType, Description )
		SELECT new.CF_ID, "Trigger", "HG Destination", CONCAT(new.OverrideDestination, " Activated");
	END IF;
		
	IF new.OverrideDestination <> old.OverrideDestination THEN
		SET new.changeDate = Now();
		INSERT INTO ChangeLog( CF_ID, ChangedBy, ChangeType, Description )
		SELECT new.CF_ID, "Trigger", "HG Destination", CONCAT(new.OverrideDestination, " Activated");
	END IF;

END */;;
DELIMITER ;
/*!50003 SET sql_mode              = @saved_sql_mode */ ;
/*!50003 SET character_set_client  = @saved_cs_client */ ;
/*!50003 SET character_set_results = @saved_cs_results */ ;
/*!50003 SET collation_connection  = @saved_col_connection */ ;

--
-- Table structure for table `HuntGroup_LastCall`
--

DROP TABLE IF EXISTS `HuntGroup_LastCall`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `HuntGroup_LastCall` (
  `HGLC_ID` int(11) NOT NULL AUTO_INCREMENT,
  `HGL_ID` int(11) NOT NULL DEFAULT '0' COMMENT 'Points to the specific list',
  `S_ID` int(11) NOT NULL DEFAULT '0' COMMENT 'Points to ServiceNumber',
  `A_Number` varchar(50) NOT NULL DEFAULT '0' COMMENT 'A_Number of caller',
  `C_Number` varchar(50) NOT NULL DEFAULT '0' COMMENT 'Number of called HG member',
  `TimeOfCall` datetime NOT NULL COMMENT 'Timestamp of last call',
  `Counter` int(11) NOT NULL COMMENT 'Count number of times this LastCall is used',
  PRIMARY KEY (`HGLC_ID`),
  KEY `A_Number` (`A_Number`),
  KEY `S_ID` (`S_ID`),
  KEY `HGL_ID` (`HGL_ID`)
) ENGINE=InnoDB AUTO_INCREMENT=11 DEFAULT CHARSET=utf8 COMMENT='Stores the last called member of hunt group by specific caller.';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `HuntGroup_List`
--

DROP TABLE IF EXISTS `HuntGroup_List`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `HuntGroup_List` (
  `HGL_ID` int(11) NOT NULL AUTO_INCREMENT,
  `CF_ID` int(11) DEFAULT NULL,
  `MID` int(11) DEFAULT NULL,
  `HG_ID` int(11) NOT NULL,
  `ListName` varchar(50) DEFAULT NULL COMMENT 'Name of call list, used by web front-end',
  `EnableLastCall` int(1) DEFAULT '0' COMMENT '0 - False; 1 - True; enables the LastCall feature',
  PRIMARY KEY (`HGL_ID`),
  KEY `CF_ID_MID` (`CF_ID`,`MID`)
) ENGINE=InnoDB AUTO_INCREMENT=89 DEFAULT CHARSET=latin1 COMMENT='All call lists used by this call flow';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `HuntGroup_ListMember`
--

DROP TABLE IF EXISTS `HuntGroup_ListMember`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `HuntGroup_ListMember` (
  `HGLM_ID` int(11) NOT NULL AUTO_INCREMENT,
  `CF_ID` int(11) DEFAULT NULL,
  `MID` int(11) DEFAULT NULL,
  `HGL_ID` int(11) DEFAULT NULL COMMENT 'Pointer to hunt group list',
  `HGM_ID` int(11) DEFAULT NULL COMMENT 'Pointer to member',
  `Active` int(1) DEFAULT '1' COMMENT '0 - false, 1- true',
  `RingTimeout` int(11) DEFAULT '30' COMMENT 'Allowed time to ring no answer, in seconds',
  `Weight` int(11) DEFAULT '100' COMMENT 'Not used yet',
  `Sequence` int(11) DEFAULT NULL COMMENT 'Holds the order of members to be called',
  PRIMARY KEY (`HGLM_ID`),
  KEY `CF_ID_MID` (`CF_ID`,`MID`)
) ENGINE=InnoDB AUTO_INCREMENT=93 DEFAULT CHARSET=latin1 ROW_FORMAT=COMPACT COMMENT='Links members to a specific list used by this call flow';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `HuntGroup_Member`
--

DROP TABLE IF EXISTS `HuntGroup_Member`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `HuntGroup_Member` (
  `HGM_ID` int(11) NOT NULL AUTO_INCREMENT,
  `CF_ID` int(11) DEFAULT NULL,
  `MID` int(11) DEFAULT NULL,
  `HG_ID` int(11) DEFAULT '0',
  `Description` varchar(255) DEFAULT NULL,
  `DestinationNumber` varchar(45) DEFAULT NULL,
  `LoginStatus` varchar(45) DEFAULT 'LOGGED_ON' COMMENT 'LOGGED_OFF, LOGGED_ON',
  `LoginPin` varchar(10) DEFAULT NULL COMMENT 'For use by DIA, Dial In Agents',
  `StartDate` date DEFAULT NULL,
  `EndDate` date DEFAULT NULL,
  PRIMARY KEY (`HGM_ID`),
  KEY `CF_ID_MID` (`CF_ID`,`MID`)
) ENGINE=InnoDB AUTO_INCREMENT=78 DEFAULT CHARSET=latin1 ROW_FORMAT=COMPACT COMMENT='All numbers belonging to this call flow';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `IVR`
--

DROP TABLE IF EXISTS `IVR`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `IVR` (
  `CF_ID` int(11) NOT NULL,
  `MID` int(11) NOT NULL,
  `Description` varchar(255) NOT NULL DEFAULT '',
  `Timeout` int(11) DEFAULT NULL COMMENT 'Max time for user to enter a digit',
  `TimeoutMID` int(11) DEFAULT NULL COMMENT 'Next module when time out',
  `IllegalEntryMID` int(11) DEFAULT '0' COMMENT 'Next module when illegal entry',
  `NextMID` int(11) DEFAULT '0' COMMENT 'Used by web page',
  `StartDate` date DEFAULT NULL,
  `EndDate` date DEFAULT NULL,
  PRIMARY KEY (`CF_ID`,`MID`)
) ENGINE=InnoDB DEFAULT CHARSET=latin1 COMMENT='Interactive voice response';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `IVR_node`
--

DROP TABLE IF EXISTS `IVR_node`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `IVR_node` (
  `CF_ID` int(11) NOT NULL,
  `MID` int(11) NOT NULL,
  `IN_ID` int(11) NOT NULL,
  `Digit` varchar(1) DEFAULT NULL COMMENT 'Digit entered by caller',
  `Description` varchar(255) DEFAULT NULL,
  `VoicemailBox` varchar(50) DEFAULT NULL COMMENT 'Identifies VM box if each node has seperate VM.',
  `NextMID` int(11) DEFAULT NULL COMMENT 'Next module for this digit',
  `NextListId` int(11) DEFAULT NULL COMMENT 'If next module is HuntGroup, use this List',
  `StartDate` date DEFAULT NULL,
  `EndDate` date DEFAULT NULL,
  PRIMARY KEY (`IN_ID`,`MID`,`CF_ID`),
  KEY `FK_Ivr` (`CF_ID`,`MID`),
  CONSTRAINT `FK_Ivr` FOREIGN KEY (`CF_ID`, `MID`) REFERENCES `IVR` (`CF_ID`, `MID`)
) ENGINE=InnoDB DEFAULT CHARSET=latin1 COMMENT='Conatains the action for each digit selected in the IVR		';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Temporary table structure for view `LastCLosed`
--

DROP TABLE IF EXISTS `LastCLosed`;
/*!50001 DROP VIEW IF EXISTS `LastCLosed`*/;
SET @saved_cs_client     = @@character_set_client;
SET character_set_client = utf8;
/*!50001 CREATE VIEW `LastCLosed` AS SELECT 
 1 AS `cf_id`,
 1 AS `max(ChangeDate)`*/;
SET character_set_client = @saved_cs_client;

--
-- Table structure for table `MID_To_Table`
--

DROP TABLE IF EXISTS `MID_To_Table`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `MID_To_Table` (
  `CF_ID` int(11) NOT NULL,
  `MID` int(11) NOT NULL,
  `TableName` varchar(45) NOT NULL,
  `Description` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`MID`,`CF_ID`)
) ENGINE=InnoDB DEFAULT CHARSET=latin1 COMMENT='Maps a MID of a specific call flow to a table			';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!50003 SET @saved_cs_client      = @@character_set_client */ ;
/*!50003 SET @saved_cs_results     = @@character_set_results */ ;
/*!50003 SET @saved_col_connection = @@collation_connection */ ;
/*!50003 SET character_set_client  = utf8 */ ;
/*!50003 SET character_set_results = utf8 */ ;
/*!50003 SET collation_connection  = utf8_general_ci */ ;
/*!50003 SET @saved_sql_mode       = @@sql_mode */ ;
/*!50003 SET sql_mode              = '' */ ;
DELIMITER ;;
/*!50003 CREATE*/ /*!50017 DEFINER=`tsip`@`localhost`*/ /*!50003 TRIGGER MID_trigger
BEFORE INSERT ON MID_To_Table
FOR EACH ROW
BEGIN
    SET NEW.MID := (
        SELECT COALESCE( MAX( MID ), 0 ) + 1 FROM MID_To_Table
        WHERE CF_ID = NEW.CF_ID
    );
END */;;
DELIMITER ;
/*!50003 SET sql_mode              = @saved_sql_mode */ ;
/*!50003 SET character_set_client  = @saved_cs_client */ ;
/*!50003 SET character_set_results = @saved_cs_results */ ;
/*!50003 SET collation_connection  = @saved_col_connection */ ;

--
-- Table structure for table `PrePaidCheck`
--

DROP TABLE IF EXISTS `PrePaidCheck`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `PrePaidCheck` (
  `PPC_ID` int(11) NOT NULL AUTO_INCREMENT,
  `CF_ID` int(11) NOT NULL DEFAULT '0',
  `MID` int(11) NOT NULL DEFAULT '0',
  `WelcomeMessage` varchar(255) NOT NULL DEFAULT '' COMMENT 'If populated play the welcome message',
  `ChargeMID` int(11) NOT NULL DEFAULT '0' COMMENT 'Not used',
  `ContinueMID` int(11) NOT NULL DEFAULT '0',
  PRIMARY KEY (`PPC_ID`),
  KEY `CF_ID_MID` (`CF_ID`,`MID`)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8 COMMENT='Module to check the PrePaid db if money is available';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `PrePaidUpdate`
--

DROP TABLE IF EXISTS `PrePaidUpdate`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `PrePaidUpdate` (
  `PPU_ID` int(11) NOT NULL AUTO_INCREMENT,
  `CF_ID` int(11) NOT NULL DEFAULT '0',
  `MID` int(11) NOT NULL DEFAULT '0',
  `NextMID` int(11) NOT NULL DEFAULT '0',
  PRIMARY KEY (`PPU_ID`)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `Queue`
--

DROP TABLE IF EXISTS `Queue`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `Queue` (
  `Q_ID` int(11) NOT NULL AUTO_INCREMENT,
  `CF_ID` int(11) NOT NULL,
  `MID` int(11) NOT NULL,
  `Description` varchar(255) DEFAULT NULL,
  `ShowOverride` int(1) DEFAULT '0' COMMENT '0 - false, 1- true - If true, UI will show the override number field',
  `OverrideNumber` varchar(45) DEFAULT '0' COMMENT 'If populated, this number will be called, not the queue member.',
  `OverrideActive` int(1) DEFAULT '0' COMMENT '0 - false, 1- true - If true, the OverideNumber will be used',
  `ConnectedCallTimeout` int(11) DEFAULT '60' COMMENT 'Max call time, 0 is unlimited, in minutes',
  `ConnectedCallWarning` int(1) DEFAULT '0' COMMENT '0 - false, 1- true - Give a beep warning max time  is close',
  `AllowExtendedCall` int(1) DEFAULT '0' COMMENT '0 - false, 1- true - Allow caller to enter DTMF 5 to extend call one more period',
  `WaitMusic` varchar(45) DEFAULT NULL COMMENT 'Not used now',
  `AnswerQueuePolicy` varchar(45) DEFAULT 'NO_ANSWER' COMMENT 'NO_ANSWER, BEFORE, AFTER',
  `RingTonePolicy` varchar(45) DEFAULT 'FAKE_RINGING' COMMENT 'TRUE_RINGING, FAKE_RINGING, MUSIC',
  `UseTrueANumber` int(1) DEFAULT '0' COMMENT '0 - false, 1- true',
  `UseSpecificANumber` varchar(50) DEFAULT NULL COMMENT 'If populated, this will be a-number for outgoing call.',
  `AnnounceCall` int(1) DEFAULT '1' COMMENT '0 - false, 1- true - Announce call to called party when answered',
  `AnnounceCallMsgType` int(11) DEFAULT '0' COMMENT '0 - Standard, 1- User Defined (manual) (If AnnounceCall == true)',
  `PresentNumber` int(1) DEFAULT '1' COMMENT '0 - false, 1- true (If AnnounceCall == true) - Present the called service number',
  `PresentName` int(1) DEFAULT '0' COMMENT '0 - false, 1- true (If AnnounceCall == true) - Present the called service name',
  `QueueingEnabled` int(1) DEFAULT '0' COMMENT '0 - false, 1- true - Enable the queue feature when called party is tsip-busy',
  `GivePosition` int(1) DEFAULT '0' COMMENT '0 - false, 1- true - Give position in queue',
  `GivePositionInterval` int(11) DEFAULT '60' COMMENT 'seconds - Interval for which to give queue position',
  `AllowCallback` int(1) DEFAULT '0' COMMENT '0 - false, 1- true - Allows caller to enter DTMF 7 for a callback',
  `AlertCallerWhenFree` int(1) DEFAULT '0' COMMENT '0 - false, 1- true - Allows caller to enter DTMF 5 to be alerted by SMS when queue is empty',
  `SmsText` varchar(80) DEFAULT 'Empty' COMMENT 'Text of queue empty alert SMS',
  `BusyRecordingType` int(1) DEFAULT '0' COMMENT '0 - Standard, 1- User Defined (from dim)',
  `EnableCallMonitoring` int(1) DEFAULT '0' COMMENT '0 - false, 1- true - Monitor busy and unanswered calls',
  `RecordCall` int(1) DEFAULT '0' COMMENT '0 - false, 1- true - Record the call',
  `DtmfAcceptCall` int(1) DEFAULT '0' COMMENT '0 - false, 1- true   (RingAll only) - Called party must enter DTMF 5 to accept call.',
  `BusyMID` int(11) DEFAULT '0' COMMENT 'Next module when called party is busy (outside tsip or queue not enabled)',
  `NoAnswerMID` int(1) DEFAULT '0' COMMENT 'Next MID after call not answered (and not busy)',
  `NextMID` int(1) DEFAULT '0' COMMENT 'Next MID after successful call',
  `StartDate` date DEFAULT NULL,
  `EndDate` date DEFAULT NULL,
  PRIMARY KEY (`Q_ID`),
  KEY `CF_ID_MID` (`CF_ID`,`MID`)
) ENGINE=InnoDB AUTO_INCREMENT=37 DEFAULT CHARSET=latin1 COMMENT='Conatain all parameters of the queue feature';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `Queue_Member`
--

DROP TABLE IF EXISTS `Queue_Member`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `Queue_Member` (
  `CF_ID` int(11) DEFAULT NULL,
  `MID` int(11) DEFAULT NULL,
  `QM_ID` int(11) NOT NULL AUTO_INCREMENT,
  `Description` varchar(255) NOT NULL DEFAULT '0',
  `DestinationNumber` varchar(45) DEFAULT NULL,
  `DestinationRoute` varchar(45) DEFAULT NULL COMMENT 'Blank for default route (props)',
  `Active` int(1) DEFAULT '1' COMMENT '0 - false, 1- true',
  `RingingTimeout` int(11) DEFAULT '30' COMMENT 'No Answer timeout, in seconds',
  `StartDate` date DEFAULT NULL,
  `EndDate` date DEFAULT NULL,
  `ChangeDate` datetime DEFAULT NULL,
  PRIMARY KEY (`QM_ID`),
  KEY `CF_ID_MID` (`CF_ID`,`MID`)
) ENGINE=InnoDB AUTO_INCREMENT=63 DEFAULT CHARSET=latin1 COMMENT='Contains the available routing numbers for this Queue, only one kan be active.';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!50003 SET @saved_cs_client      = @@character_set_client */ ;
/*!50003 SET @saved_cs_results     = @@character_set_results */ ;
/*!50003 SET @saved_col_connection = @@collation_connection */ ;
/*!50003 SET character_set_client  = utf8mb4 */ ;
/*!50003 SET character_set_results = utf8mb4 */ ;
/*!50003 SET collation_connection  = utf8mb4_general_ci */ ;
/*!50003 SET @saved_sql_mode       = @@sql_mode */ ;
/*!50003 SET sql_mode              = 'ONLY_FULL_GROUP_BY,STRICT_TRANS_TABLES,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION' */ ;
DELIMITER ;;
/*!50003 CREATE*/ /*!50017 DEFINER=`tsip`@`localhost`*/ /*!50003 TRIGGER `Queue_Member_trigger` BEFORE UPDATE ON `Queue_Member` FOR EACH ROW BEGIN

	IF new.Active <> old.Active  THEN
			SET new.changeDate = Now();
			IF new.Active THEN
				INSERT INTO ChangeLog( CF_ID, ChangedBy, ChangeType, Description )
		 		SELECT new.CF_ID, "Trigger", "Queue Member", CONCAT(new.DestinationNumber, " Activated");
			ELSE
				INSERT INTO ChangeLog( CF_ID, ChangedBy, ChangeType, Description )
		 		SELECT new.CF_ID, "Trigger", "Queue Member", CONCAT(new.DestinationNumber, " Deactivated");
			END IF;
	END IF;

END */;;
DELIMITER ;
/*!50003 SET sql_mode              = @saved_sql_mode */ ;
/*!50003 SET character_set_client  = @saved_cs_client */ ;
/*!50003 SET character_set_results = @saved_cs_results */ ;
/*!50003 SET collation_connection  = @saved_col_connection */ ;

--
-- Table structure for table `Recording`
--

DROP TABLE IF EXISTS `Recording`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `Recording` (
  `CF_ID` int(11) NOT NULL,
  `MID` int(11) NOT NULL,
  `Description` varchar(255) NOT NULL,
  `Destination` varchar(45) DEFAULT NULL,
  `NextModule` int(11) DEFAULT NULL,
  PRIMARY KEY (`MID`,`CF_ID`)
) ENGINE=InnoDB DEFAULT CHARSET=latin1 COMMENT='TBD	';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `RingBack`
--

DROP TABLE IF EXISTS `RingBack`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `RingBack` (
  `RB_ID` int(11) NOT NULL AUTO_INCREMENT,
  `CF_ID` int(11) NOT NULL,
  `MID` int(11) NOT NULL,
  `IntroFilename` varchar(255) DEFAULT NULL COMMENT 'Intro Voicefile to play',
  `ConfirmFilename` varchar(255) DEFAULT NULL COMMENT 'Voicefile when ringback is confirmed',
  `Digit` varchar(1) DEFAULT '4' COMMENT 'Digit to be pressed for RingBack',
  `TimeToWait` int(11) DEFAULT '10' COMMENT 'How many seconds to wait for "digit"',
  `AnswerCallPolicy` varchar(50) DEFAULT 'NO_ANSWER' COMMENT 'NO_ANSWER, BEFORE, AFTER',
  `NextMID` int(11) DEFAULT '0' COMMENT 'MID for ringback activated',
  `TimeoutMID` int(11) DEFAULT '0' COMMENT 'MID when ringback NOT chosen',
  PRIMARY KEY (`RB_ID`),
  KEY `CF_ID_MID` (`CF_ID`,`MID`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `RouteCall`
--

DROP TABLE IF EXISTS `RouteCall`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `RouteCall` (
  `CF_ID` int(11) NOT NULL,
  `MID` int(11) NOT NULL,
  `Description` varchar(255) DEFAULT NULL,
  `DestinationNumber` varchar(45) DEFAULT NULL,
  `DestinationRoute` varchar(45) DEFAULT NULL COMMENT 'Blank for default route (props)',
  `NightServiceActive` int(1) DEFAULT '0' COMMENT '0 - false, 1- true; if true, forward to night service number in transaction',
  `UseTrueANumber` int(1) DEFAULT '1' COMMENT '0 - false, 1- true',
  `CallerID` varchar(50) DEFAULT '' COMMENT 'Overrides UseTrueANumber if populated',
  `AnswerCallPolicy` varchar(50) DEFAULT 'NO_ANSWER' COMMENT 'NO_ANSWER, BEFORE, AFTER',
  `NoAnswerTimer` int(11) DEFAULT '0' COMMENT 'Max time to ring without answer',
  `ChargeOnBusy` int(1) DEFAULT '0' COMMENT '0 - false, 1- true - Answer A-leg when B-leg is BUSY',
  `DelayDisconnect` int(1) DEFAULT '0' COMMENT 'How many ms to wait after connect.',
  `BusyAsFailureInterval` int(1) DEFAULT '0' COMMENT 'How many seconds before a busy signal can be handled as a failure',
  `MobileOnly` int(1) DEFAULT '0' COMMENT '0 - false, 1- true - Allow only calls from mobiles',
  `LandlineOnly` int(1) DEFAULT '0' COMMENT '0 - false, 1- true - Allow only calls from landlines',
  `OnBusyNextMID` int(11) DEFAULT '0' COMMENT 'Next module when called party busy',
  `OnFailNextMID` int(11) DEFAULT '0' COMMENT 'Next module when call fails',
  `OnNoAnswerNextMID` int(11) DEFAULT '0' COMMENT 'Next module when called party does not answer',
  `OnDisconnectNextMID` int(11) DEFAULT '0' COMMENT 'Next module when call disconnects',
  `OnDisconnectDropCall` int(1) DEFAULT '1' COMMENT '0 - false, 1- true; drop whole call after this module',
  `PassThrough` int(1) DEFAULT '0' COMMENT '0 - false, 1- true - When true, do not route call, go to next MID',
  `OnPassThroughNextMID` int(11) DEFAULT '0' COMMENT 'Used when pass through active',
  `RecordCall` int(11) DEFAULT '0' COMMENT '0 - false, 1- true; record the call',
  `StartDate` date DEFAULT NULL,
  `EndDate` date DEFAULT NULL,
  PRIMARY KEY (`MID`,`CF_ID`)
) ENGINE=InnoDB DEFAULT CHARSET=latin1 COMMENT='Contains parameter for simple routing of calls.			';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `SMS`
--

DROP TABLE IF EXISTS `SMS`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `SMS` (
  `CF_ID` int(11) NOT NULL,
  `MID` int(11) NOT NULL,
  `Description` varchar(255) DEFAULT 'descr',
  `Text` varchar(255) DEFAULT '',
  `SourceNumber` varchar(255) DEFAULT '' COMMENT 'If blank, use B Number',
  `SendToANumber` int(1) DEFAULT '0' COMMENT '0 - false, 1- true',
  `SendToCNumber` int(1) DEFAULT '0' COMMENT '0 - false, 1- true. Number found in transaction object',
  `Destinations` varchar(512) DEFAULT '' COMMENT 'Comma seperated list',
  `SendDelay` int(11) DEFAULT '0' COMMENT 'How many ms delay before SMS is sent (after disconnect if enabled)',
  `AnswerCall` int(1) DEFAULT '0' COMMENT '0 - false, 1- true',
  `DisconnectCall` int(1) DEFAULT '0' COMMENT '0 - false, 1- true',
  `DelayToDisconnect` int(11) DEFAULT '0' COMMENT 'How many ms delay before disconnect',
  `Price` varchar(50) DEFAULT '00.00',
  `AuthenticationCode` varchar(50) DEFAULT '' COMMENT 'Blank to use default, fill out for specific kode',
  `NextMID` int(11) NOT NULL DEFAULT '0' COMMENT 'Next MID if OK or if failMID is null',
  `FailMID` int(11) NOT NULL DEFAULT '0' COMMENT 'Next MID if failure',
  PRIMARY KEY (`MID`,`CF_ID`)
) ENGINE=InnoDB DEFAULT CHARSET=latin1 COMMENT='TBD	';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `Schedule`
--

DROP TABLE IF EXISTS `Schedule`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `Schedule` (
  `CF_ID` int(11) NOT NULL,
  `MID` int(11) NOT NULL,
  `ScheduleType` varchar(55) DEFAULT 'MANUAL' COMMENT 'MANUAL, WEEKLY, ADVANCED',
  `ManualState` varchar(55) DEFAULT 'UNKNOWN' COMMENT 'CLOSED, OPEN, UNKNOWN',
  `OpenMID` int(11) DEFAULT '0' COMMENT 'Next module when OPEN (MANUAL)',
  `OpenListId` int(11) DEFAULT '0' COMMENT 'Can be used if next module is HuntGroup  (MANUAL)',
  `ClosedMID` int(11) DEFAULT '0' COMMENT 'Next module when CLOSED  (MANUAL)',
  `ClosedListId` int(11) DEFAULT '0' COMMENT 'Can be used if next module is HuntGroup  (MANUAL)',
  `ClosedRecordingType` int(11) DEFAULT '1' COMMENT '0 - Standard, 1- User Defined (from dim)',
  `ClosedCallCounter` int(11) DEFAULT '0' COMMENT 'Counts how many calls arrive while service is closed',
  `NextOpening` datetime DEFAULT NULL COMMENT 'Time of next expected opening. Only relevant when MANUAL-CLOSED.',
  `NextOpeningMessage` int(1) DEFAULT '1' COMMENT '0 - false, 1- true. If true a message is played to the caller (MANUAL and WEEKLY)',
  `NextOpeningAutoOpen` int(1) DEFAULT '0' COMMENT '0 - false, 1- true. If true the schedule is set to OPEN',
  `NextOpeningAlert` int(1) DEFAULT '0' COMMENT '0 - false, 1- true. If true an SMS is sent to C-party when auto set OPEN',
  `NextOpeningAlertNumber` varchar(50) DEFAULT '0' COMMENT 'The number where the alert SMS is to be sent',
  `ScheduleDefinition` varchar(4096) DEFAULT NULL COMMENT 'JSON string defining the schedule (WEEKLY and ADVANCED)',
  `WeeklyClosedMID` int(11) DEFAULT '0' COMMENT 'Next module when OPEN (WEEKLY)',
  `WeeklyClosedListId` int(11) DEFAULT '0' COMMENT 'Can be used if next module is HuntGroup (WEEKLY)',
  `WeeklyClosedPlayMessage` int(1) DEFAULT '0' COMMENT '0 - false, 1- true.',
  `AlertCallerOnOpen` int(1) DEFAULT '0' COMMENT '0 - false, 1- true. SMS is sent to caller (A-party)',
  `StartDate` datetime DEFAULT NULL COMMENT 'Start date of this schedule',
  `EndDate` datetime DEFAULT NULL COMMENT 'End date of this schedule',
  `ChangeDate` datetime DEFAULT NULL COMMENT 'Last time schedule was changed (MANUAL)',
  PRIMARY KEY (`MID`,`CF_ID`),
  KEY `CF_ID_MID` (`CF_ID`,`MID`)
) ENGINE=InnoDB DEFAULT CHARSET=latin1 COMMENT='Contains parameters for the Schedule feature.	';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!50003 SET @saved_cs_client      = @@character_set_client */ ;
/*!50003 SET @saved_cs_results     = @@character_set_results */ ;
/*!50003 SET @saved_col_connection = @@collation_connection */ ;
/*!50003 SET character_set_client  = utf8mb4 */ ;
/*!50003 SET character_set_results = utf8mb4 */ ;
/*!50003 SET collation_connection  = utf8mb4_general_ci */ ;
/*!50003 SET @saved_sql_mode       = @@sql_mode */ ;
/*!50003 SET sql_mode              = 'ONLY_FULL_GROUP_BY,STRICT_TRANS_TABLES,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION' */ ;
DELIMITER ;;
/*!50003 CREATE*/ /*!50017 DEFINER=`tsip`@`localhost`*/ /*!50003 TRIGGER `Schedule_Change_Trigger` BEFORE UPDATE ON `Schedule` FOR EACH ROW BEGIN

	IF new.ScheduleType <> old.ScheduleType THEN
		IF new.ScheduleType = "WEEKLY" THEN
			UPDATE Service
			SET State = 3
			WHERE SG_ID = (SELECT SG_ID FROM ServiceGroup WHERE CF_ID = new.CF_ID AND AlwaysOpen = 1);
		END IF;
	END IF;

   IF new.ScheduleType <> old.ScheduleType THEN
			IF new.ScheduleType = "WEEKLY" THEN
				INSERT INTO ChangeLog( CF_ID, ChangedBy, ChangeType, Description )
		 		SELECT new.CF_ID, "Trigger", "ScheduleType", "WEEKLY";
			ELSE
				INSERT INTO ChangeLog( CF_ID, ChangedBy, ChangeType, Description )
		 		SELECT new.CF_ID, "Trigger", "ScheduleType", "MANUAL";
			END IF;
	END IF;

	IF new.ManualState <> old.ManualState  THEN
		SET new.ChangeDate = Now();
		
		IF new.ManualState = "OPEN" THEN
			INSERT INTO ScheduleChange( CF_ID, ChangeTime, MID )
	 		SELECT new.CF_ID, Now(), new.MID; 
			
			IF new.ScheduleType = "WEEKLY" THEN
				INSERT INTO ChangeLog( CF_ID, ChangedBy, ChangeType, Description )
		 		SELECT new.CF_ID, "Trigger", "ScheduleWeekly", "OPEN";
			ELSE
				INSERT INTO ChangeLog( CF_ID, ChangedBy, ChangeType, Description )
		 		SELECT new.CF_ID, "Trigger", "Schedule", "OPEN";
			END IF;	 		
	 		SET new.NextOpening = NULL;
	 		
			UPDATE Service
     		SET State = 3
		   WHERE SG_ID = (SELECT SG_ID FROM ServiceGroup WHERE CF_ID = new.CF_ID);

	 	END IF;

		IF new.ManualState = "CLOSED" THEN
			DELETE FROM ScheduleChange
	 		WHERE CF_ID = new.CF_ID;

			IF new.NextOpening IS NULL THEN
				IF new.ScheduleType = "WEEKLY" THEN
					INSERT INTO ChangeLog( CF_ID, ChangedBy, ChangeType, Description )
			 		VALUES( new.CF_ID, "Trigger", "ScheduleWeekly", "CLOSED" );
			 	ELSE
					INSERT INTO ChangeLog( CF_ID, ChangedBy, ChangeType, Description )
			 		VALUES( new.CF_ID, "Trigger", "Schedule", "CLOSED" );
			 	END IF;
		 	ELSE 
				INSERT INTO ChangeLog( CF_ID, ChangedBy, ChangeType, Description )
		 		VALUES( new.CF_ID, "Trigger", "Schedule", CONCAT("CLOSED (", new.NextOpening, ") [", new.NextOpeningAutoOpen, "]" ) );
		 	END IF;

			IF new.ScheduleType = "WEEKLY" THEN
				UPDATE Service
	     		SET State = 2
			   WHERE SG_ID = (SELECT SG_ID FROM ServiceGroup WHERE CF_ID = new.CF_ID AND AlwaysOpen = 0);
		 	ELSE
				UPDATE Service
	     		SET State = 2
			   WHERE SG_ID = (SELECT SG_ID FROM ServiceGroup WHERE CF_ID = new.CF_ID);
		 	END IF;
	 	END IF;   	 	
	END IF;

	IF NOT new.NextOpening IS NULL AND NOT old.NextOpening IS NULL AND new.NextOpening <> old.NextOpening THEN
		INSERT INTO ChangeLog( CF_ID, ChangedBy, ChangeType, Description )
 		VALUES( new.CF_ID, "Trigger", "Schedule", CONCAT("CLOSED (", new.NextOpening, ") [", new.NextOpeningAutoOpen, "]" ) );
 	END IF;

END */;;
DELIMITER ;
/*!50003 SET sql_mode              = @saved_sql_mode */ ;
/*!50003 SET character_set_client  = @saved_cs_client */ ;
/*!50003 SET character_set_results = @saved_cs_results */ ;
/*!50003 SET collation_connection  = @saved_col_connection */ ;

--
-- Table structure for table `ScheduleAlertCaller`
--

DROP TABLE IF EXISTS `ScheduleAlertCaller`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `ScheduleAlertCaller` (
  `SA_ID` int(11) NOT NULL AUTO_INCREMENT,
  `CF_ID` int(11) NOT NULL COMMENT 'Call FLow where alert is ordered',
  `SourceNumber` varchar(50) NOT NULL COMMENT 'Surce number of sent SMS',
  `DestinationNumber` varchar(50) NOT NULL COMMENT 'Mobile number to alert',
  `AlertType` varchar(50) DEFAULT NULL COMMENT 'SMS | tbd',
  `SmsText` varchar(255) DEFAULT NULL COMMENT 'Alert text',
  `Created` datetime DEFAULT NULL COMMENT 'Can be used to expired too old alerts.',
  KEY `SA_ID` (`SA_ID`)
) ENGINE=InnoDB DEFAULT CHARSET=latin1 COMMENT='List over callers who have ordered an SMS alert when a service opens.';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!50003 SET @saved_cs_client      = @@character_set_client */ ;
/*!50003 SET @saved_cs_results     = @@character_set_results */ ;
/*!50003 SET @saved_col_connection = @@collation_connection */ ;
/*!50003 SET character_set_client  = utf8mb4 */ ;
/*!50003 SET character_set_results = utf8mb4 */ ;
/*!50003 SET collation_connection  = utf8mb4_general_ci */ ;
/*!50003 SET @saved_sql_mode       = @@sql_mode */ ;
/*!50003 SET sql_mode              = 'ONLY_FULL_GROUP_BY,STRICT_TRANS_TABLES,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION' */ ;
DELIMITER ;;
/*!50003 CREATE*/ /*!50017 DEFINER=`tsip`@`localhost`*/ /*!50003 TRIGGER `ScheduleAlertCaller_TriggerInsert` BEFORE INSERT ON `ScheduleAlertCaller` FOR EACH ROW BEGIN

		INSERT INTO ChangeLog( CF_ID, ChangedBy, ChangeType, Description )
 		SELECT new.CF_ID, "Trigger", "ScheduleAlertCaller", CONCAT("Alert request ", new.DestinationNumber);

END */;;
DELIMITER ;
/*!50003 SET sql_mode              = @saved_sql_mode */ ;
/*!50003 SET character_set_client  = @saved_cs_client */ ;
/*!50003 SET character_set_results = @saved_cs_results */ ;
/*!50003 SET collation_connection  = @saved_col_connection */ ;
/*!50003 SET @saved_cs_client      = @@character_set_client */ ;
/*!50003 SET @saved_cs_results     = @@character_set_results */ ;
/*!50003 SET @saved_col_connection = @@collation_connection */ ;
/*!50003 SET character_set_client  = utf8mb4 */ ;
/*!50003 SET character_set_results = utf8mb4 */ ;
/*!50003 SET collation_connection  = utf8mb4_general_ci */ ;
/*!50003 SET @saved_sql_mode       = @@sql_mode */ ;
/*!50003 SET sql_mode              = 'ONLY_FULL_GROUP_BY,STRICT_TRANS_TABLES,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION' */ ;
DELIMITER ;;
/*!50003 CREATE*/ /*!50017 DEFINER=`tsip`@`localhost`*/ /*!50003 TRIGGER `ScheduleAlertCaller_TriggerDelete` AFTER DELETE ON `ScheduleAlertCaller` FOR EACH ROW BEGIN

		INSERT INTO ChangeLog( CF_ID, ChangedBy, ChangeType, Description )
 		SELECT old.CF_ID, "Trigger", "ScheduleAlertCaller", CONCAT("Alert sent to ", old.DestinationNumber);

END */;;
DELIMITER ;
/*!50003 SET sql_mode              = @saved_sql_mode */ ;
/*!50003 SET character_set_client  = @saved_cs_client */ ;
/*!50003 SET character_set_results = @saved_cs_results */ ;
/*!50003 SET collation_connection  = @saved_col_connection */ ;

--
-- Table structure for table `ScheduleAlertHG`
--

DROP TABLE IF EXISTS `ScheduleAlertHG`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `ScheduleAlertHG` (
  `SA_ID` int(11) NOT NULL,
  `NR_ID` int(11) NOT NULL,
  `CF_ID` int(11) NOT NULL,
  `MID` int(11) NOT NULL,
  `AlertType` varchar(50) DEFAULT NULL COMMENT 'SMS | tbd',
  `NextAlert` date DEFAULT NULL,
  `SmsText` varchar(255) DEFAULT NULL,
  `Created` date DEFAULT NULL,
  KEY `SA_ID` (`SA_ID`)
) ENGINE=InnoDB DEFAULT CHARSET=latin1 ROW_FORMAT=COMPACT COMMENT='TBD\r\nList of hunt groups where each active member will get SMS when schedule becomes OPEN';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `ScheduleChange`
--

DROP TABLE IF EXISTS `ScheduleChange`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `ScheduleChange` (
  `CF_ID` int(11) NOT NULL,
  `ChangeTime` datetime NOT NULL,
  `MID` int(11) NOT NULL,
  PRIMARY KEY (`CF_ID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `ScheduleTemporary`
--

DROP TABLE IF EXISTS `ScheduleTemporary`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `ScheduleTemporary` (
  `CF_ID` int(11) NOT NULL,
  `MID` int(11) NOT NULL,
  `ST_ID` int(11) NOT NULL AUTO_INCREMENT,
  `Description` varchar(255) DEFAULT NULL COMMENT 'Describe temp schedule, e.g. Easter 2018',
  `Schedule` varchar(4096) DEFAULT NULL COMMENT 'JSON string',
  `ClosedRecordingType` int(1) DEFAULT '0' COMMENT '0 - Standard, 1- User Defined (from dim)',
  `StartDate` date DEFAULT NULL,
  `EndDate` date DEFAULT NULL,
  PRIMARY KEY (`ST_ID`),
  KEY `FK_Schedule` (`CF_ID`,`MID`),
  CONSTRAINT `FK_Schedule` FOREIGN KEY (`CF_ID`, `MID`) REFERENCES `Schedule` (`CF_ID`, `MID`)
) ENGINE=InnoDB DEFAULT CHARSET=latin1 COMMENT='A temporary schedule which overides the fixed schedule, typical holidays etc.';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `Selector`
--

DROP TABLE IF EXISTS `Selector`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `Selector` (
  `SEL_ID` int(11) NOT NULL AUTO_INCREMENT,
  `CF_ID` int(11) NOT NULL,
  `MID` int(11) NOT NULL,
  `HEAD_NODE_ID` int(11) NOT NULL COMMENT 'Points to the head node of the selector chain',
  `AdminNumber` varchar(50) DEFAULT '0' COMMENT 'Number for admin role',
  `AdminPin` varchar(50) DEFAULT '0' COMMENT 'PIN code for admin users',
  `PublicNumber` varchar(50) DEFAULT '0' COMMENT 'Number for public role',
  `SingleNumber` varchar(50) DEFAULT '0' COMMENT 'Number for single role',
  `DeleteOldRecording` int(1) DEFAULT '0' COMMENT '0 - false; 1 - true',
  `ExpiryStrategy` int(11) DEFAULT '0' COMMENT '0 - none; 1 - weekday, 2 - date',
  `ExpiryNodeID` int(11) DEFAULT '0',
  `AutoStoreRecording` int(1) DEFAULT '0' COMMENT '0 - false; 1 - true : If true the admin need not enter 4 to save recording',
  `NextMID` int(11) DEFAULT '0',
  PRIMARY KEY (`SEL_ID`,`CF_ID`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `SelectorNode`
--

DROP TABLE IF EXISTS `SelectorNode`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `SelectorNode` (
  `NODE_ID` int(11) NOT NULL AUTO_INCREMENT,
  `SEL_ID` int(11) NOT NULL DEFAULT '0',
  `Parent_NODE_ID` int(11) DEFAULT '0' COMMENT 'Parent in chain of Selector nodes',
  `IsTail` int(1) NOT NULL DEFAULT '0' COMMENT '0 - False; 1 - True. Is tail of selector chain',
  `RecordAtTail` int(1) NOT NULL DEFAULT '0' COMMENT '0 - False; 1 - True. Tail node shall be a recording (for "Admin")',
  `Level` int(2) NOT NULL DEFAULT '0' COMMENT 'Level in chain, level = 1 is head',
  `Digit` varchar(1) NOT NULL DEFAULT '0' COMMENT 'Digit chosen by parent, "*" if always selected',
  `InfoMessageText` varchar(255) NOT NULL DEFAULT '0' COMMENT 'Information message for this node',
  `InfoMessageFile` varchar(255) NOT NULL DEFAULT '0' COMMENT 'Information message for this node',
  `EmptyMessageFile` varchar(255) NOT NULL DEFAULT '0' COMMENT 'If tail level and no nodes recorded for public',
  `Description` varchar(255) NOT NULL DEFAULT '0' COMMENT 'E.g. "Track", "Country","Weekday"',
  PRIMARY KEY (`NODE_ID`,`SEL_ID`)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `SelectorNodePrompt`
--

DROP TABLE IF EXISTS `SelectorNodePrompt`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `SelectorNodePrompt` (
  `SNP_ID` int(11) NOT NULL AUTO_INCREMENT,
  `NODE_ID` int(11) NOT NULL DEFAULT '0',
  `SEL_ID` int(11) NOT NULL DEFAULT '0',
  `Digit` varchar(1) NOT NULL DEFAULT '0',
  `MessageText` varchar(255) DEFAULT NULL COMMENT 'E.g. "For Bjerke travbane, Tast1"',
  `MessageFile` varchar(255) DEFAULT NULL,
  `ChosenFile` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`SNP_ID`)
) ENGINE=InnoDB AUTO_INCREMENT=29 DEFAULT CHARSET=utf8;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `SelectorRecording`
--

DROP TABLE IF EXISTS `SelectorRecording`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `SelectorRecording` (
  `SR_ID` int(11) NOT NULL AUTO_INCREMENT,
  `SEL_ID` int(11) NOT NULL DEFAULT '0',
  `SelectorPath` varchar(50) NOT NULL DEFAULT '0',
  `RecordingPath` varchar(255) NOT NULL DEFAULT '0',
  `State` int(1) NOT NULL DEFAULT '0' COMMENT '1 - New, 2 - Active, 3 - Old',
  `Created` datetime DEFAULT NULL,
  `Expires` datetime DEFAULT NULL,
  PRIMARY KEY (`SR_ID`)
) ENGINE=InnoDB AUTO_INCREMENT=50 DEFAULT CHARSET=utf8;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `Service`
--

DROP TABLE IF EXISTS `Service`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `Service` (
  `NR_ID` int(11) NOT NULL AUTO_INCREMENT,
  `SG_ID` int(11) DEFAULT NULL COMMENT 'Points to service group',
  `C_ID` int(11) DEFAULT NULL COMMENT 'Points to Customer as SG_ID may be null',
  `ServiceNumber` varchar(45) NOT NULL COMMENT 'The Service Number',
  `Description` varchar(45) DEFAULT NULL,
  `IsMasterNumber` int(1) DEFAULT '0' COMMENT '0 - false, 1- true. If true this is a master number for a set of shortnumbers',
  `MasterMessageFileName` varchar(255) DEFAULT '' COMMENT 'Message to be played when this number is Master Number',
  `PrepaidNumber` int(1) DEFAULT '0' COMMENT '0 - false, 1- true',
  `State` int(1) DEFAULT '1' COMMENT '0 - Restricted, 1 - Unknown, 2 - Closed, 3 - Open, 4 - Busy',
  `ServiceCategory_ID` int(11) DEFAULT '0' COMMENT 'Pointing to the category of this service',
  `DialInPIN` varchar(10) DEFAULT '0' COMMENT 'The PIN for the DIM',
  `NightServiceNumber` varchar(15) DEFAULT NULL COMMENT 'Forward to this number in RouteCall',
  `AllowDialInManagement` int(1) DEFAULT '0' COMMENT '0 - false, 1- true',
  `EndServiceMessageId` int(11) DEFAULT '0' COMMENT 'ID of common recording to be played if EndDate reached',
  `AnnounceNewNumber` varchar(20) DEFAULT NULL COMMENT 'New number that caller can dial',
  `EnableCallMonitoring` int(1) DEFAULT '0' COMMENT '0 - false, 1- true',
  `AllowStateMonitoring` int(1) DEFAULT '1' COMMENT '0 - false, 1- true',
  `CallMonitoringEmail` varchar(100) DEFAULT NULL COMMENT 'List of emails to send call monitoring warnings',
  `UseWhitelist` int(1) DEFAULT '0' COMMENT '0 - false, 1- true. If true, only allow a-numbers in the whitelist through',
  `StartDate` date DEFAULT NULL,
  `EndDate` date DEFAULT NULL,
  `FrozenDate` date DEFAULT NULL,
  `Comment` varchar(255) DEFAULT NULL COMMENT 'Free text comment',
  PRIMARY KEY (`NR_ID`),
  KEY `FK_ServiceGroupID` (`SG_ID`),
  KEY `FK_ServiceCategoryID` (`ServiceCategory_ID`),
  CONSTRAINT `FK_ServiceCategoryID` FOREIGN KEY (`ServiceCategory_ID`) REFERENCES `ServiceCategory` (`SC_ID`),
  CONSTRAINT `FK_ServiceGroupID` FOREIGN KEY (`SG_ID`) REFERENCES `ServiceGroup` (`SG_ID`) ON DELETE NO ACTION
) ENGINE=InnoDB AUTO_INCREMENT=159 DEFAULT CHARSET=latin1 COMMENT='Contains the service number owned by a customer';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!50003 SET @saved_cs_client      = @@character_set_client */ ;
/*!50003 SET @saved_cs_results     = @@character_set_results */ ;
/*!50003 SET @saved_col_connection = @@collation_connection */ ;
/*!50003 SET character_set_client  = utf8 */ ;
/*!50003 SET character_set_results = utf8 */ ;
/*!50003 SET collation_connection  = utf8_general_ci */ ;
/*!50003 SET @saved_sql_mode       = @@sql_mode */ ;
/*!50003 SET sql_mode              = '' */ ;
DELIMITER ;;
/*!50003 CREATE*/ /*!50017 DEFINER=`tsip`@`localhost`*/ /*!50003 TRIGGER service_start_date
BEFORE INSERT ON Service
FOR EACH ROW
BEGIN
    SET NEW.StartDate := COALESCE( NEW.StartDate, now() );
END */;;
DELIMITER ;
/*!50003 SET sql_mode              = @saved_sql_mode */ ;
/*!50003 SET character_set_client  = @saved_cs_client */ ;
/*!50003 SET character_set_results = @saved_cs_results */ ;
/*!50003 SET collation_connection  = @saved_col_connection */ ;
/*!50003 SET @saved_cs_client      = @@character_set_client */ ;
/*!50003 SET @saved_cs_results     = @@character_set_results */ ;
/*!50003 SET @saved_col_connection = @@collation_connection */ ;
/*!50003 SET character_set_client  = utf8mb4 */ ;
/*!50003 SET character_set_results = utf8mb4 */ ;
/*!50003 SET collation_connection  = utf8mb4_general_ci */ ;
/*!50003 SET @saved_sql_mode       = @@sql_mode */ ;
/*!50003 SET sql_mode              = 'ONLY_FULL_GROUP_BY,STRICT_TRANS_TABLES,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION' */ ;
DELIMITER ;;
/*!50003 CREATE*/ /*!50017 DEFINER=`tfsven`@`%`*/ /*!50003 TRIGGER `Service_DSG_Trigger` BEFORE UPDATE ON `Service` FOR EACH ROW BEGIN

	IF new.SG_ID IS NULL AND old.SG_ID IS NOT NULL THEN
		INSERT INTO ChangeLog( CF_ID, ServiceNumber, ChangedBy, ChangeType, Description )
 		SELECT old.SG_ID, new.ServiceNumber, "Trigger", "Service", CONCAT(new.ServiceNumber,  " ", COALESCE(old.SG_ID, ""), " >> ...."  );

	ELSEIF new.SG_ID IS NOT NULL AND old.SG_ID IS NULL THEN
		INSERT INTO ChangeLog( CF_ID, ServiceNumber, ChangedBy, ChangeType, Description )
 		SELECT new.SG_ID, new.ServiceNumber, "Trigger", "Service", CONCAT(new.ServiceNumber, " ....", " >> " , COALESCE(new.SG_ID, "") );

	ELSEIF new.SG_ID <> old.SG_ID THEN
		INSERT INTO ChangeLog( CF_ID, ServiceNumber, ChangedBy, ChangeType, Description )
 		SELECT new.SG_ID, new.ServiceNumber, "Trigger", "Service", CONCAT(new.ServiceNumber, " ", COALESCE(old.SG_ID, ""), " >> ", COALESCE(new.SG_ID, "") );
	
	END IF;

END */;;
DELIMITER ;
/*!50003 SET sql_mode              = @saved_sql_mode */ ;
/*!50003 SET character_set_client  = @saved_cs_client */ ;
/*!50003 SET character_set_results = @saved_cs_results */ ;
/*!50003 SET collation_connection  = @saved_col_connection */ ;

--
-- Table structure for table `ServiceCategory`
--

DROP TABLE IF EXISTS `ServiceCategory`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `ServiceCategory` (
  `SC_ID` int(11) NOT NULL AUTO_INCREMENT,
  `ServiceCategoryName` varchar(50) NOT NULL DEFAULT '0',
  PRIMARY KEY (`SC_ID`)
) ENGINE=InnoDB AUTO_INCREMENT=22 DEFAULT CHARSET=latin1 COMMENT='A list of service categories used by each service. Used for debugging, statistics and web page adjustments';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `ServiceGroup`
--

DROP TABLE IF EXISTS `ServiceGroup`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `ServiceGroup` (
  `SG_ID` int(11) NOT NULL AUTO_INCREMENT,
  `C_ID` int(11) DEFAULT '0' COMMENT 'Points to customer',
  `Description` varchar(50) DEFAULT '0',
  `CF_ID` int(11) DEFAULT '0' COMMENT 'Points to Call Flow of this service group',
  `AlwaysOpen` int(1) DEFAULT '0' COMMENT '0 - false; 1 - true. Call flow always open, even if a schedule is closed',
  PRIMARY KEY (`SG_ID`),
  KEY `FK_CustomerID` (`C_ID`),
  CONSTRAINT `FK_CustomerID` FOREIGN KEY (`C_ID`) REFERENCES `Customer` (`C_ID`)
) ENGINE=InnoDB AUTO_INCREMENT=154 DEFAULT CHARSET=utf8;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `ServiceGroupStatistics`
--

DROP TABLE IF EXISTS `ServiceGroupStatistics`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `ServiceGroupStatistics` (
  `SGS_ID` int(11) NOT NULL AUTO_INCREMENT,
  `SG_ID` int(11) DEFAULT '0' COMMENT 'Point to a specific service group',
  `NR_ID` int(11) NOT NULL DEFAULT '0' COMMENT 'Point to a service which can be viewed by that service group',
  KEY `sgs_ID` (`SGS_ID`),
  KEY `FK_ServiceGroupStatistics_Service` (`NR_ID`),
  KEY `FK_ServiceGroupStatistics_ServiceGroup` (`SG_ID`),
  CONSTRAINT `FK_ServiceGroupStatistics_Service` FOREIGN KEY (`NR_ID`) REFERENCES `Service` (`NR_ID`),
  CONSTRAINT `FK_ServiceGroupStatistics_ServiceGroup` FOREIGN KEY (`SG_ID`) REFERENCES `ServiceGroup` (`SG_ID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COMMENT='This will restrict access to statistics. \r\nIf a servicegroup has no entries here then there are no restrictions. \r\nIf there are entries then this servicegroup can only see statistics for that service';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `ServiceLastUsed`
--

DROP TABLE IF EXISTS `ServiceLastUsed`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `ServiceLastUsed` (
  `Master_Number` varchar(50) NOT NULL DEFAULT '0',
  `A_Number` varchar(50) NOT NULL DEFAULT '0',
  `Service_Number` varchar(50) NOT NULL DEFAULT '0',
  `FirstMID` int(11) NOT NULL DEFAULT '0',
  UNIQUE KEY `Master_Number_A_Number` (`Master_Number`,`A_Number`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `ServiceMarkedCall`
--

DROP TABLE IF EXISTS `ServiceMarkedCall`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `ServiceMarkedCall` (
  `SMC_ID` int(11) NOT NULL AUTO_INCREMENT COMMENT 'Primary key for this tables',
  `S_ID` int(11) NOT NULL DEFAULT '0' COMMENT 'Points to the service which had marked call',
  `ANumber` varchar(50) NOT NULL DEFAULT '0' COMMENT 'The caller number of the marked call',
  `BNumber` varchar(50) NOT NULL DEFAULT '0' COMMENT 'The service number of the marked call',
  `CNumber` varchar(50) NOT NULL DEFAULT '0' COMMENT 'The receiving number of the marked call.',
  `Timestamp` varchar(50) NOT NULL DEFAULT '0' COMMENT 'The timestamp of the marker',
  `CallID` varchar(50) DEFAULT '0' COMMENT 'The original_call_id that is marked.',
  PRIMARY KEY (`SMC_ID`)
) ENGINE=InnoDB AUTO_INCREMENT=6 DEFAULT CHARSET=utf8;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `Service_Announcement`
--

DROP TABLE IF EXISTS `Service_Announcement`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `Service_Announcement` (
  `CF_ID` int(11) NOT NULL,
  `MID` int(11) NOT NULL,
  `SA_ID` int(11) NOT NULL AUTO_INCREMENT,
  `ServiceNumber` int(11) NOT NULL DEFAULT '0' COMMENT 'The service which this announcement belongs to',
  `Filename` varchar(255) DEFAULT '0',
  `Date` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`CF_ID`,`MID`,`ServiceNumber`),
  KEY `SA_ID` (`SA_ID`)
) ENGINE=InnoDB AUTO_INCREMENT=12 DEFAULT CHARSET=utf8;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `ShortNumber`
--

DROP TABLE IF EXISTS `ShortNumber`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `ShortNumber` (
  `MasterNumber` varchar(50) NOT NULL DEFAULT '0',
  `ShortNumber` varchar(50) NOT NULL DEFAULT '0',
  `ServiceNumber` varchar(50) NOT NULL DEFAULT '0',
  `FirstMID` int(11) DEFAULT '0',
  UNIQUE KEY `MasterNumber_ShortNumber_ServiceNumber` (`MasterNumber`,`ShortNumber`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `TimeLimit`
--

DROP TABLE IF EXISTS `TimeLimit`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `TimeLimit` (
  `TL_ID` int(11) NOT NULL AUTO_INCREMENT,
  `SC_ID` int(11) DEFAULT '0' COMMENT 'To which ServiceCategory this limit belongs ',
  `A_Number` varchar(50) DEFAULT NULL COMMENT 'To which specific a_no this limit belongs  (future)',
  `Service_Number` varchar(50) DEFAULT NULL COMMENT 'To which specific service_no this limit belongs ',
  `Description` varchar(255) NOT NULL DEFAULT '0',
  `Period` int(11) NOT NULL DEFAULT '0' COMMENT '1-day, 2-week, 3-month, 4-year',
  `Floating` int(1) NOT NULL DEFAULT '0' COMMENT '0-false; 1-true',
  `MaxMinutes` int(11) NOT NULL DEFAULT '0' COMMENT 'Max minutes for given period',
  `CommonRecording_ID` int(11) NOT NULL DEFAULT '0' COMMENT 'Recording to be played when over limit',
  PRIMARY KEY (`TL_ID`),
  KEY `SC_ID` (`SC_ID`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8 COMMENT='Limits can be placed on callers accumulated time over specified period\r\nThe limits can be applied to (in priority order)\r\n- Specific caller to specific service number (future)\r\n- Specific caller to a service category (future)\r\n- Specific caller to any category/service (future)\r\n- Any caller to a specific service number\r\n- Any caller to a service category\r\n';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `TimeLimitException`
--

DROP TABLE IF EXISTS `TimeLimitException`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `TimeLimitException` (
  `TLE_ID` int(11) NOT NULL AUTO_INCREMENT,
  `TL_ID` int(11) NOT NULL DEFAULT '0',
  `A_Number` varchar(50) NOT NULL DEFAULT '0',
  `Percentage` int(11) NOT NULL DEFAULT '0' COMMENT 'How many percent caller is allowed compared to time limit',
  PRIMARY KEY (`TLE_ID`),
  KEY `A_Number` (`A_Number`),
  KEY `TL_ID` (`TL_ID`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8 COMMENT='Allows callers to have an increase/decrease on the time limit';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `TimeLimitLog`
--

DROP TABLE IF EXISTS `TimeLimitLog`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `TimeLimitLog` (
  `TLL_ID` int(11) NOT NULL AUTO_INCREMENT,
  `TL_ID` int(11) NOT NULL DEFAULT '0',
  `Timestamp` datetime NOT NULL,
  `Period` int(1) NOT NULL,
  `A_Number` varchar(50) NOT NULL,
  `B_Number` varchar(50) NOT NULL,
  PRIMARY KEY (`TLL_ID`)
) ENGINE=InnoDB AUTO_INCREMENT=27 DEFAULT CHARSET=utf8;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Temporary table structure for view `View_LastTimeClosed`
--

DROP TABLE IF EXISTS `View_LastTimeClosed`;
/*!50001 DROP VIEW IF EXISTS `View_LastTimeClosed`*/;
SET @saved_cs_client     = @@character_set_client;
SET character_set_client = utf8;
/*!50001 CREATE VIEW `View_LastTimeClosed` AS SELECT 
 1 AS `cf_id`,
 1 AS `max_ChangeDate`*/;
SET character_set_client = @saved_cs_client;

--
-- Temporary table structure for view `View_LastTimeOpen`
--

DROP TABLE IF EXISTS `View_LastTimeOpen`;
/*!50001 DROP VIEW IF EXISTS `View_LastTimeOpen`*/;
SET @saved_cs_client     = @@character_set_client;
SET character_set_client = utf8;
/*!50001 CREATE VIEW `View_LastTimeOpen` AS SELECT 
 1 AS `cf_id`,
 1 AS `max_ChangeDate`*/;
SET character_set_client = @saved_cs_client;

--
-- Temporary table structure for view `View_MissedCallsSinceClosed`
--

DROP TABLE IF EXISTS `View_MissedCallsSinceClosed`;
/*!50001 DROP VIEW IF EXISTS `View_MissedCallsSinceClosed`*/;
SET @saved_cs_client     = @@character_set_client;
SET character_set_client = utf8;
/*!50001 CREATE VIEW `View_MissedCallsSinceClosed` AS SELECT 
 1 AS `CF_ID`,
 1 AS `a_number`,
 1 AS `b_number`*/;
SET character_set_client = @saved_cs_client;

--
-- Temporary table structure for view `View_ServiceStates`
--

DROP TABLE IF EXISTS `View_ServiceStates`;
/*!50001 DROP VIEW IF EXISTS `View_ServiceStates`*/;
SET @saved_cs_client     = @@character_set_client;
SET character_set_client = utf8;
/*!50001 CREATE VIEW `View_ServiceStates` AS SELECT 
 1 AS `ServiceNumber`,
 1 AS `State`*/;
SET character_set_client = @saved_cs_client;

--
-- Table structure for table `Voicemail`
--

DROP TABLE IF EXISTS `Voicemail`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `Voicemail` (
  `VM_ID` int(11) NOT NULL AUTO_INCREMENT,
  `CF_ID` int(11) NOT NULL,
  `MID` int(11) NOT NULL,
  `Description` varchar(255) DEFAULT NULL,
  `StandardGreeting` int(1) DEFAULT '1' COMMENT '0 - false; 1 - true; If true use standard greeting.',
  `UserGreeting` varchar(255) DEFAULT NULL COMMENT 'URL to user specific grreeting',
  `UnifyInbox` int(11) DEFAULT '1' COMMENT '0 - false; 1 - true; If false display one inbox per b_no',
  `MWI_Email` varchar(255) DEFAULT NULL COMMENT 'List of email adresses for MWI',
  `MWI_Email_Sender` varchar(255) DEFAULT NULL COMMENT 'Address of sender',
  `MWI_Email_Subject` varchar(255) DEFAULT NULL COMMENT 'Subject of email',
  `MWI_SMS` varchar(255) DEFAULT NULL COMMENT 'List of mobile numbers for MWI',
  `MWI_FirstOnly` int(1) DEFAULT NULL COMMENT 'Only send MWI for first message per day',
  `RetrievalNumber` varchar(50) DEFAULT NULL COMMENT 'Number to be included in MWI for VM retrieval',
  `StartDate` date DEFAULT NULL,
  `EndDate` date DEFAULT NULL,
  PRIMARY KEY (`VM_ID`),
  KEY `CF_ID_MID` (`CF_ID`,`MID`)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=latin1 COMMENT='TBD	';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `VoicemailMessage`
--

DROP TABLE IF EXISTS `VoicemailMessage`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `VoicemailMessage` (
  `VMM_ID` int(11) NOT NULL AUTO_INCREMENT,
  `Timestamp` datetime NOT NULL,
  `FileName` varchar(255) DEFAULT NULL,
  `A_Number` varchar(50) DEFAULT NULL,
  `VMbox_Number` varchar(50) NOT NULL DEFAULT '' COMMENT 'Normally the service number',
  `Length` int(11) DEFAULT '0',
  `State` varchar(50) DEFAULT NULL COMMENT 'VM_UNREAD,VM_READ, VM_ARCHIVED',
  `ReadBy` varchar(50) DEFAULT NULL COMMENT 'Username which read the message',
  `ReadDate` datetime DEFAULT NULL COMMENT 'When the message is read',
  `Archived` varchar(50) DEFAULT NULL COMMENT 'Name of voicemail archive',
  PRIMARY KEY (`VMbox_Number`,`Timestamp`),
  KEY `B_Number` (`VMbox_Number`),
  KEY `State` (`State`),
  KEY `VMM_ID` (`VMM_ID`)
) ENGINE=InnoDB AUTO_INCREMENT=49 DEFAULT CHARSET=utf8;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `VoicemailRetrieval`
--

DROP TABLE IF EXISTS `VoicemailRetrieval`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `VoicemailRetrieval` (
  `VMR_ID` int(11) NOT NULL AUTO_INCREMENT,
  `CF_ID` int(11) NOT NULL DEFAULT '0',
  `MID` int(11) NOT NULL DEFAULT '0',
  `Service_Number` varchar(50) NOT NULL DEFAULT '0' COMMENT 'The number of this VMR service',
  `VM_Box` varchar(20) NOT NULL DEFAULT '0' COMMENT 'ID of the VM box, normally the service number',
  `PIN_Code` varchar(10) NOT NULL DEFAULT '0' COMMENT 'PIN Code to access this vm box',
  `Whitelist` varchar(255) NOT NULL DEFAULT '0' COMMENT 'List of number allowed to rerieve this vm box',
  PRIMARY KEY (`VMR_ID`),
  KEY `VM_Box` (`VM_Box`),
  KEY `Service_Number` (`Service_Number`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=latin1;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `Whitelist`
--

DROP TABLE IF EXISTS `Whitelist`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `Whitelist` (
  `WL_ID` int(11) NOT NULL AUTO_INCREMENT,
  `A_Number` varchar(50) NOT NULL DEFAULT '0',
  `NR_ID` varchar(50) NOT NULL DEFAULT '0' COMMENT 'Which Service this whitelisting concerns',
  PRIMARY KEY (`WL_ID`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Final view structure for view `LastCLosed`
--

/*!50001 DROP VIEW IF EXISTS `LastCLosed`*/;
/*!50001 SET @saved_cs_client          = @@character_set_client */;
/*!50001 SET @saved_cs_results         = @@character_set_results */;
/*!50001 SET @saved_col_connection     = @@collation_connection */;
/*!50001 SET character_set_client      = utf8mb4 */;
/*!50001 SET character_set_results     = utf8mb4 */;
/*!50001 SET collation_connection      = utf8mb4_general_ci */;
/*!50001 CREATE ALGORITHM=UNDEFINED */
/*!50013 DEFINER=`tfsven`@`%` SQL SECURITY DEFINER */
/*!50001 VIEW `LastCLosed` AS select `ChangeLog`.`CF_ID` AS `cf_id`,max(`ChangeLog`.`ChangeDate`) AS `max(ChangeDate)` from `ChangeLog` where (`ChangeLog`.`Description` like 'CLOSED%') group by `ChangeLog`.`CF_ID` */;
/*!50001 SET character_set_client      = @saved_cs_client */;
/*!50001 SET character_set_results     = @saved_cs_results */;
/*!50001 SET collation_connection      = @saved_col_connection */;

--
-- Final view structure for view `View_LastTimeClosed`
--

/*!50001 DROP VIEW IF EXISTS `View_LastTimeClosed`*/;
/*!50001 SET @saved_cs_client          = @@character_set_client */;
/*!50001 SET @saved_cs_results         = @@character_set_results */;
/*!50001 SET @saved_col_connection     = @@collation_connection */;
/*!50001 SET character_set_client      = utf8mb4 */;
/*!50001 SET character_set_results     = utf8mb4 */;
/*!50001 SET collation_connection      = utf8mb4_general_ci */;
/*!50001 CREATE ALGORITHM=UNDEFINED */
/*!50013 DEFINER=`tsip`@`%` SQL SECURITY DEFINER */
/*!50001 VIEW `View_LastTimeClosed` AS select `ChangeLog`.`CF_ID` AS `cf_id`,max(`ChangeLog`.`ChangeDate`) AS `max_ChangeDate` from `ChangeLog` where (`ChangeLog`.`Description` like 'CLOSED%') group by `ChangeLog`.`CF_ID` */;
/*!50001 SET character_set_client      = @saved_cs_client */;
/*!50001 SET character_set_results     = @saved_cs_results */;
/*!50001 SET collation_connection      = @saved_col_connection */;

--
-- Final view structure for view `View_LastTimeOpen`
--

/*!50001 DROP VIEW IF EXISTS `View_LastTimeOpen`*/;
/*!50001 SET @saved_cs_client          = @@character_set_client */;
/*!50001 SET @saved_cs_results         = @@character_set_results */;
/*!50001 SET @saved_col_connection     = @@collation_connection */;
/*!50001 SET character_set_client      = utf8mb4 */;
/*!50001 SET character_set_results     = utf8mb4 */;
/*!50001 SET collation_connection      = utf8mb4_general_ci */;
/*!50001 CREATE ALGORITHM=UNDEFINED */
/*!50013 DEFINER=`tsip`@`%` SQL SECURITY DEFINER */
/*!50001 VIEW `View_LastTimeOpen` AS select `ChangeLog`.`CF_ID` AS `cf_id`,max(`ChangeLog`.`ChangeDate`) AS `max_ChangeDate` from `ChangeLog` where (`ChangeLog`.`Description` = 'OPEN') group by `ChangeLog`.`CF_ID` */;
/*!50001 SET character_set_client      = @saved_cs_client */;
/*!50001 SET character_set_results     = @saved_cs_results */;
/*!50001 SET collation_connection      = @saved_col_connection */;

--
-- Final view structure for view `View_MissedCallsSinceClosed`
--

/*!50001 DROP VIEW IF EXISTS `View_MissedCallsSinceClosed`*/;
/*!50001 SET @saved_cs_client          = @@character_set_client */;
/*!50001 SET @saved_cs_results         = @@character_set_results */;
/*!50001 SET @saved_col_connection     = @@collation_connection */;
/*!50001 SET character_set_client      = utf8mb4 */;
/*!50001 SET character_set_results     = utf8mb4 */;
/*!50001 SET collation_connection      = utf8mb4_general_ci */;
/*!50001 CREATE ALGORITHM=UNDEFINED */
/*!50013 DEFINER=`tfsven`@`%` SQL SECURITY DEFINER */
/*!50001 VIEW `View_MissedCallsSinceClosed` AS select `cf`.`CF_ID` AS `CF_ID`,`cdr`.`cdr`.`a_number` AS `a_number`,`cdr`.`cdr`.`b_number` AS `b_number` from (`cdr`.`cdr` join `customers`.`CallFlow` `cf`) where (`cf`.`CF_ID` = 9999) */;
/*!50001 SET character_set_client      = @saved_cs_client */;
/*!50001 SET character_set_results     = @saved_cs_results */;
/*!50001 SET collation_connection      = @saved_col_connection */;

--
-- Final view structure for view `View_ServiceStates`
--

/*!50001 DROP VIEW IF EXISTS `View_ServiceStates`*/;
/*!50001 SET @saved_cs_client          = @@character_set_client */;
/*!50001 SET @saved_cs_results         = @@character_set_results */;
/*!50001 SET @saved_col_connection     = @@collation_connection */;
/*!50001 SET character_set_client      = utf8mb4 */;
/*!50001 SET character_set_results     = utf8mb4 */;
/*!50001 SET collation_connection      = utf8mb4_general_ci */;
/*!50001 CREATE ALGORITHM=UNDEFINED */
/*!50013 DEFINER=`tsip`@`%` SQL SECURITY DEFINER */
/*!50001 VIEW `View_ServiceStates` AS select `Service`.`ServiceNumber` AS `ServiceNumber`,if((`Service`.`AllowStateMonitoring` = 0),0,`Service`.`State`) AS `State` from `Service` */;
/*!50001 SET character_set_client      = @saved_cs_client */;
/*!50001 SET character_set_results     = @saved_cs_results */;
/*!50001 SET collation_connection      = @saved_col_connection */;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2020-05-12 11:39:14
