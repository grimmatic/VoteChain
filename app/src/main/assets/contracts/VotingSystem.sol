// SPDX-License-Identifier: MIT
pragma solidity ^0.8.0;

/**
 * @title Oylama Sistemi Akıllı Sözleşmesi
 * @dev Bu sözleşme, blockchain üzerinde güvenli ve şeffaf bir oylama sistemi sağlar.
 * @author Sevban SARIÇİÇEK, Özgün Ecem DOK
 * @notice VoteChain bitirme projesi için geliştirilmiştir
 */
contract VotingSystem {
    
    struct Candidate {
        uint id;
        string name;
        string party;
        uint voteCount;
    }
    
    struct Election {
        uint id;
        string name;
        string description;
        uint startTime;
        uint endTime;
        bool active;
        mapping(uint => Candidate) candidates;
        uint candidateCount;
    }
    
    // Seçimler deposu
    mapping(uint => Election) public elections;
    uint public electionCount;
    
    // Oy verme kaydı
    mapping(address => mapping(uint => bool)) public hasVoted;
    
    // TC Kimlik doğrulama için
    mapping(string => bool) public validTCIds;
    
    // Admin adresleri
    mapping(address => bool) public isAdmin;
    
    // İşlem logları için olaylar
    event ElectionCreated(uint indexed electionId, string name, uint startTime, uint endTime);
    event CandidateAdded(uint indexed electionId, uint indexed candidateId, string name);
    event VoteCast(uint indexed electionId, uint indexed candidateId, address indexed voter);
    event TCIDAdded(string hashedTCID);
    
    // Sadece admin rolüne sahip adresler
    modifier onlyAdmin() {
        require(isAdmin[msg.sender], "Only admin can perform this action");
        _;
    }
    
    /**
     * @dev Sözleşme oluşturulurken çağrılır
     */
    constructor() {
        // Sözleşmeyi dağıtan adres admin olur
        isAdmin[msg.sender] = true;
    }
    
    /**
     * @dev Yeni bir admin ekler
     * @param _admin Yeni admin adresi
     */
    function addAdmin(address _admin) public onlyAdmin {
        isAdmin[_admin] = true;
    }
    
    /**
     * @dev Yeni bir seçim oluşturur
     * @param _name Seçim adı
     * @param _description Seçim açıklaması
     * @param _startTime Başlangıç zamanı (unix timestamp)
     * @param _endTime Bitiş zamanı (unix timestamp)
     */
    function createElection(
        string memory _name,
        string memory _description,
        uint _startTime,
        uint _endTime
    ) public onlyAdmin {
        require(_startTime < _endTime, "End time must be after start time");
        
        electionCount++;
        
        Election storage election = elections[electionCount];
        election.id = electionCount;
        election.name = _name;
        election.description = _description;
        election.startTime = _startTime;
        election.endTime = _endTime;
        election.active = true;
        election.candidateCount = 0;
        
        emit ElectionCreated(electionCount, _name, _startTime, _endTime);
    }
    
    /**
     * @dev Bir seçime aday ekler
     * @param _electionId Seçim ID'si
     * @param _name Aday adı
     * @param _party Aday partisi
     */
    function addCandidate(uint _electionId, string memory _name, string memory _party) public onlyAdmin {
        require(_electionId > 0 && _electionId <= electionCount, "Invalid election ID");
        
        Election storage election = elections[_electionId];
        require(election.active, "Election is not active");
        
        election.candidateCount++;
        
        Candidate storage candidate = election.candidates[election.candidateCount];
        candidate.id = election.candidateCount;
        candidate.name = _name;
        candidate.party = _party;
        candidate.voteCount = 0;
        
        emit CandidateAdded(_electionId, election.candidateCount, _name);
    }
    
    /**
     * @dev TC Kimlik numarası hash'ini geçerli kimlikler listesine ekler
     * @param _tcIdHash TC Kimlik numarasının hash'i
     */
    function addValidTCId(string memory _tcIdHash) public onlyAdmin {
        validTCIds[_tcIdHash] = true;
        emit TCIDAdded(_tcIdHash);
    }
    
    /**
     * @dev Oy kullanma işlemi
     * @param _electionId Seçim ID'si
     * @param _candidateId Aday ID'si
     * @param _tcIdHash TC Kimlik numarasının hash'i
     */
    function vote(uint _electionId, uint _candidateId, string memory _tcIdHash) public {
        // TC Kimlik kontrolü
        require(validTCIds[_tcIdHash], "Invalid TC ID");
        
        // Seçimin geçerliliğini kontrol et
        require(_electionId > 0 && _electionId <= electionCount, "Invalid election ID");
        
        Election storage election = elections[_electionId];
        require(election.active, "Election is not active");
        require(block.timestamp >= election.startTime, "Election has not started yet");
        require(block.timestamp <= election.endTime, "Election has ended");
        
        // Daha önce oy kullanılmadığını kontrol et
        require(!hasVoted[msg.sender][_electionId], "Already voted in this election");
        
        // Adayın geçerli olduğunu kontrol et
        require(_candidateId > 0 && _candidateId <= election.candidateCount, "Invalid candidate");
        
        // Oyu kaydet
        election.candidates[_candidateId].voteCount++;
        
        // Oy kullanma kaydı
        hasVoted[msg.sender][_electionId] = true;
        
        emit VoteCast(_electionId, _candidateId, msg.sender);
    }
    
    /**
     * @dev Bir seçimin bilgilerini döndürür
     * @param _electionId Seçim ID'si
     * @return id, name, description, startTime, endTime, active
     */
    function getElection(uint _electionId) public view returns (
        uint id,
        string memory name,
        string memory description,
        uint startTime,
        uint endTime,
        bool active
    ) {
        require(_electionId > 0 && _electionId <= electionCount, "Invalid election ID");
        Election storage election = elections[_electionId];
        
        return (
            election.id,
            election.name,
            election.description,
            election.startTime,
            election.endTime,
            election.active
        );
    }
    
    /**
     * @dev Bir adayın bilgilerini döndürür
     * @param _electionId Seçim ID'si
     * @param _candidateId Aday ID'si
     * @return id, name, party, voteCount
     */
    function getCandidate(uint _electionId, uint _candidateId) public view returns (
        uint id,
        string memory name,
        string memory party,
        uint voteCount
    ) {
        require(_electionId > 0 && _electionId <= electionCount, "Invalid election ID");
        require(_candidateId > 0 && _candidateId <= elections[_electionId].candidateCount, "Invalid candidate ID");
        
        Candidate storage candidate = elections[_electionId].candidates[_candidateId];
        
        return (
            candidate.id,
            candidate.name,
            candidate.party,
            candidate.voteCount
        );
    }
    
    /**
     * @dev Bir seçimin tüm sonuçlarını döndürür
     * @param _electionId Seçim ID'si
     * @return ids, names, parties, voteCounts
     */
    function getElectionResults(uint _electionId) public view returns (
        uint[] memory ids,
        string[] memory names,
        string[] memory parties,
        uint[] memory voteCounts
    ) {
        require(_electionId > 0 && _electionId <= electionCount, "Invalid election ID");
        
        Election storage election = elections[_electionId];
        uint candidateCount = election.candidateCount;
        
        ids = new uint[](candidateCount);
        names = new string[](candidateCount);
        parties = new string[](candidateCount);
        voteCounts = new uint[](candidateCount);
        
        for (uint i = 1; i <= candidateCount; i++) {
            Candidate storage candidate = election.candidates[i];
            ids[i-1] = candidate.id;
            names[i-1] = candidate.name;
            parties[i-1] = candidate.party;
            voteCounts[i-1] = candidate.voteCount;
        }
        
        return (ids, names, parties, voteCounts);
    }
    
    /**
     * @dev Bir seçimin durumunu değiştirir (aktif/pasif)
     * @param _electionId Seçim ID'si
     * @param _active Yeni durum
     */
    function setElectionActive(uint _electionId, bool _active) public onlyAdmin {
        require(_electionId > 0 && _electionId <= electionCount, "Invalid election ID");
        elections[_electionId].active = _active;
    }
    
    /**
     * @dev Bir TC Kimlik numarasının geçerli olup olmadığını kontrol eder
     * @param _tcIdHash TC Kimlik numarasının hash'i
     * @return Geçerlilik durumu
     */
    function isValidTCId(string memory _tcIdHash) public view returns (bool) {
        return validTCIds[_tcIdHash];
    }
    
    /**
     * @dev Bir kullanıcının belirli bir seçimde oy kullanıp kullanmadığını kontrol eder
     * @param _voter Kullanıcı adresi
     * @param _electionId Seçim ID'si
     * @return Oy kullanma durumu
     */
    function hasUserVoted(address _voter, uint _electionId) public view returns (bool) {
        return hasVoted[_voter][_electionId];
    }
}