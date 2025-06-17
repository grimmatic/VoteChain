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

    struct Vote {
        string tcHash;
        uint electionId;
        uint candidateId;
        uint timestamp;
        address voter;
    }

    // Seçimler deposu
    mapping(uint => Election) public elections;
    uint public electionCount;

    // Oy kayıtları - her oy için benzersiz ID
    mapping(uint => Vote) public votes;
    uint public voteCount;

    // TC Hash ile oy verme kontrolü - bir TC sadece bir seçimde bir kez oy verebilir
    mapping(string => mapping(uint => bool)) public tcHashVoted; // tcHash => electionId => voted


    mapping(address => bool) public isAdmin;


    event ElectionCreated(uint indexed electionId, string name, uint startTime, uint endTime);
    event CandidateAdded(uint indexed electionId, uint indexed candidateId, string name);
    event VoteCast(
        uint indexed voteId,
        uint indexed electionId,
        uint indexed candidateId,
        string tcHash,
        address voter,
        uint timestamp
    );

    modifier onlyAdmin() {
        require(isAdmin[msg.sender], "Only admin can perform this action");
        _;
    }

    constructor() {
        isAdmin[msg.sender] = true;
    }

    function addAdmin(address _admin) public onlyAdmin {
        isAdmin[_admin] = true;
    }

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
     * @dev Oy kullanma işlemi
     * @param _electionId Seçim ID'si
     * @param _candidateId Aday ID'si
     * @param _tcHash TC Kimlik numarasının hash'i
     */
    function vote(uint _electionId, uint _candidateId, string memory _tcHash) public {
        // Seçimin geçerliliğini kontrol et
        require(_electionId > 0 && _electionId <= electionCount, "Invalid election ID");

        Election storage election = elections[_electionId];
        require(election.active, "Election is not active");
       // require(block.timestamp >= election.startTime, "Election has not started yet");
       // require(block.timestamp <= election.endTime, "Election has ended");

        // TC Hash ile daha önce bu seçimde oy kullanılmadığını kontrol et
        require(!tcHashVoted[_tcHash][_electionId], "This TC ID already voted in this election");

        // Adayın geçerli olduğunu kontrol et
        require(_candidateId > 0 && _candidateId <= election.candidateCount, "Invalid candidate");

        // Oy sayısını artır
        election.candidates[_candidateId].voteCount++;

        // TC Hash oy kullanma kaydı
        tcHashVoted[_tcHash][_electionId] = true;

        // Oy kaydını oluştur
        voteCount++;
        votes[voteCount] = Vote({
            tcHash: _tcHash,
            electionId: _electionId,
            candidateId: _candidateId,
            timestamp: block.timestamp,
            voter: msg.sender
        });

        emit VoteCast(voteCount, _electionId, _candidateId, _tcHash, msg.sender, block.timestamp);
    }

    /**
     * @dev Bir TC Hash'inin belirli bir seçimde oy kullanıp kullanmadığını kontrol eder
     */
    function hasTCHashVoted(string memory _tcHash, uint _electionId) public view returns (bool) {
        return tcHashVoted[_tcHash][_electionId];
    }

    /**
     * @dev Belirli bir oy kaydını getirir
     */
    function getVote(uint _voteId) public view returns (
        string memory tcHash,
        uint electionId,
        uint candidateId,
        uint timestamp,
        address voter
    ) {
        require(_voteId > 0 && _voteId <= voteCount, "Invalid vote ID");
        Vote storage vote = votes[_voteId];
        return (vote.tcHash, vote.electionId, vote.candidateId, vote.timestamp, vote.voter);
    }

    /**
     * @dev Bir seçimin tüm oylarını getirir
     */
    function getElectionVotes(uint _electionId) public view returns (
        uint[] memory voteIds,
        string[] memory tcHashes,
        uint[] memory candidateIds,
        uint[] memory timestamps,
        address[] memory voters
    ) {
        require(_electionId > 0 && _electionId <= electionCount, "Invalid election ID");

        // Önce bu seçime ait oy sayısını say
        uint electionVoteCount = 0;
        for (uint i = 1; i <= voteCount; i++) {
            if (votes[i].electionId == _electionId) {
                electionVoteCount++;
            }
        }

        // Dizileri oluştur
        voteIds = new uint[](electionVoteCount);
        tcHashes = new string[](electionVoteCount);
        candidateIds = new uint[](electionVoteCount);
        timestamps = new uint[](electionVoteCount);
        voters = new address[](electionVoteCount);

        // Verileri doldur
        uint index = 0;
        for (uint i = 1; i <= voteCount; i++) {
            if (votes[i].electionId == _electionId) {
                voteIds[index] = i;
                tcHashes[index] = votes[i].tcHash;
                candidateIds[index] = votes[i].candidateId;
                timestamps[index] = votes[i].timestamp;
                voters[index] = votes[i].voter;
                index++;
            }
        }

        return (voteIds, tcHashes, candidateIds, timestamps, voters);
    }


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

    function setElectionActive(uint _electionId, bool _active) public onlyAdmin {
        require(_electionId > 0 && _electionId <= electionCount, "Invalid election ID");
        elections[_electionId].active = _active;
    }
}